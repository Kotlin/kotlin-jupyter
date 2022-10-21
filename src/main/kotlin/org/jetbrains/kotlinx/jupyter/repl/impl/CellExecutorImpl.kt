package org.jetbrains.kotlinx.jupyter.repl.impl

import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.HTML
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.VariableDeclaration
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.libraryDefinition
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.config.currentKotlinVersion
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.exceptions.rethrowAsLibraryException
import org.jetbrains.kotlinx.jupyter.joinToLines
import org.jetbrains.kotlinx.jupyter.libraries.buildDependenciesInitCode
import org.jetbrains.kotlinx.jupyter.libraries.getDefinitions
import org.jetbrains.kotlinx.jupyter.log
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.NoOpDisplayHandler
import org.jetbrains.kotlinx.jupyter.repl.CellExecutor
import org.jetbrains.kotlinx.jupyter.repl.ExecutionStartedCallback
import org.jetbrains.kotlinx.jupyter.repl.InternalEvalResult
import org.jetbrains.kotlinx.jupyter.util.accepts
import java.util.LinkedList
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.withNullability

interface BaseKernelHost {
    fun <T> withHost(currentHost: KotlinKernelHost, callback: () -> T): T
}

internal class CellExecutorImpl(private val replContext: SharedReplContext) : CellExecutor {

    override fun execute(
        code: Code,
        displayHandler: DisplayHandler,
        processVariables: Boolean,
        processAnnotations: Boolean,
        processMagics: Boolean,
        invokeAfterCallbacks: Boolean,
        currentCellId: Int,
        stackFrame: ExecutionStackFrame?,
        callback: ExecutionStartedCallback?,
    ): InternalEvalResult {
        with(replContext) {
            val context = ExecutionContext(replContext, displayHandler, this@CellExecutorImpl, stackFrame.push())

            log.debug("Executing code:\n$code")
            val preprocessedCode = if (processMagics) {
                val processedMagics = codePreprocessor.process(code, context)

                log.debug("Adding ${processedMagics.libraries.size} libraries")
                val libraries = processedMagics.libraries.getDefinitions(notebook)
                context.addLibraries(libraries)

                processedMagics.code
            } else code

            if (preprocessedCode.isBlank()) {
                return InternalEvalResult(FieldValue(Unit, null), Unit)
            }

            val result = baseHost.withHost(context) {
                try {
                    evaluator.eval(preprocessedCode, currentCellId) { internalId ->
                        if (callback != null) callback(internalId, preprocessedCode)
                    }
                } catch (e: ReplException) {
                    if (e.cause is ThreadDeath) {
                        rethrowAsLibraryException(LibraryProblemPart.INTERRUPTION_CALLBACKS) {
                            interruptionCallbacksProcessor.runCallbacks()
                        }
                    }
                    throw e
                }
            }
            val snippetClass = evaluator.lastKClass

            if (processVariables) {
                log.catchAll {
                    fieldsProcessor.process(context).forEach(context::execute)
                }
            }

            if (processAnnotations) {
                log.catchAll {
                    classAnnotationsProcessor.process(snippetClass, context)
                }
            }

            // TODO: scan classloader only when new classpath was added
            log.catchAll {
                librariesScanner.addLibrariesFromClassLoader(evaluator.lastClassLoader, context, stackFrame.libraryOptions)
            }

            log.catchAll {
                rethrowAsLibraryException(LibraryProblemPart.COLOR_SCHEME_CHANGE_CALLBACKS) {
                    colorSchemeChangeCallbacksProcessor.runCallbacks()
                }
            }

            if (invokeAfterCallbacks) {
                afterCellExecution.forEach {
                    log.catchAll {
                        rethrowAsLibraryException(LibraryProblemPart.AFTER_CELL_CALLBACKS) {
                            it(context, result.scriptInstance, result.result)
                        }
                    }
                }
            }

            context.processExecutionQueue()

            return result
        }
    }

    override fun <T> execute(callback: KotlinKernelHost.() -> T): T {
        return callback(ExecutionContext(replContext, NoOpDisplayHandler, this, null.push()))
    }

    private class ExecutionContext(
        private val sharedContext: SharedReplContext,
        private val displayHandler: DisplayHandler,
        private val executor: CellExecutor,
        private val stackFrame: MutableExecutionStackFrame,
    ) : KotlinKernelHost, ExecutionHost {

        private val executionQueue = LinkedList<ExecutionCallback<*>>()

        private fun runChild(code: Code) {
            if (code.isNotBlank()) runChild(CodeExecution(code).toExecutionCallback())
        }

        private fun runChild(code: ExecutionCallback<*>) {
            execute(code)
        }

        override fun addLibraries(libraries: Collection<LibraryDefinition>) {
            if (libraries.isEmpty()) return
            stackFrame.libraries.addAll(libraries)
            for (library in libraries) {
                sharedContext.internalVariablesMarkersProcessor.registerAll(library.internalVariablesMarkers)
            }
            rethrowAsLibraryException(LibraryProblemPart.PREBUILT) {
                buildDependenciesInitCode(libraries)?.let { runChild(it) }
            }
            for (library in libraries) {
                rethrowAsLibraryException(LibraryProblemPart.INIT) {
                    library.init.forEach(::runChild)
                }
                library.renderers.mapNotNull(sharedContext.renderersProcessor::register).joinToLines().let(::runChild)
                library.throwableRenderers.forEach(sharedContext.throwableRenderersProcessor::register)
                library.converters.forEach(sharedContext.fieldsProcessor::register)
                library.classAnnotations.forEach(sharedContext.classAnnotationsProcessor::register)
                library.fileAnnotations.forEach(sharedContext.fileAnnotationsProcessor::register)
                library.interruptionCallbacks.forEach(sharedContext.interruptionCallbacksProcessor::register)
                library.colorSchemeChangedCallbacks.forEach(sharedContext.colorSchemeChangeCallbacksProcessor::register)
                sharedContext.afterCellExecution.addAll(library.afterCellExecution)
                sharedContext.codePreprocessor.addAll(library.codePreprocessors)

                val classLoader = sharedContext.evaluator.lastClassLoader
                rethrowAsLibraryException(LibraryProblemPart.RESOURCES) {
                    library.resources.forEach {
                        val htmlText = sharedContext.resourcesProcessor.wrapLibrary(it, classLoader)
                        displayHandler.handleDisplay(HTML(htmlText), this)
                    }
                }

                library.initCell.filter { !sharedContext.beforeCellExecution.contains(it) }.let(sharedContext.beforeCellExecution::addAll)
                library.shutdown.filter { !sharedContext.shutdownCodes.contains(it) }.let(sharedContext.shutdownCodes::addAll)
            }
        }

        override fun loadKotlinArtifacts(artifacts: Collection<String>, version: String?) {
            val kotlinVersion = version ?: currentKotlinVersion
            val repositories = buildList {
                if (kotlinVersion.contains("dev")) add("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
                else if (kotlinVersion.contains("SNAPSHOT")) add("*mavenLocal")
            }
            val libraries = listOf(
                libraryDefinition {
                    it.repositories = repositories
                    it.dependencies = artifacts.map { name -> "org.jetbrains.kotlin:kotlin-$name:$kotlinVersion" }
                }
            )
            buildDependenciesInitCode(libraries)?.let { scheduleExecution(it) }
        }

        override fun loadStdlibJdkExtensions(version: String?) {
            val availableExtensionsVersions = listOf(7, 8)
            val jdkVersion = JavaRuntime.versionAsInt
            val versionToLoad = availableExtensionsVersions.lastOrNull { it <= jdkVersion } ?: return
            loadKotlinArtifacts(listOf("stdlib-jdk$versionToLoad"), version)
        }

        override fun acceptsIntegrationTypeName(typeName: String): Boolean? {
            return stackFrame.traverseStack().mapNotNull { frame ->
                frame.libraries
                    .mapNotNull { library -> library.integrationTypeNameRules.accepts(typeName) }
                    .lastOrNull()
            }.firstOrNull()
        }

        override fun execute(code: Code) = executor.execute(code, displayHandler, processVariables = false, invokeAfterCallbacks = false, stackFrame = stackFrame).result

        override fun display(value: Any, id: String?) {
            displayHandler.handleDisplay(value, this, id)
        }

        override fun updateDisplay(value: Any, id: String?) {
            displayHandler.handleUpdate(value, this, id)
        }

        override fun scheduleExecution(execution: ExecutionCallback<*>) {
            executionQueue.add(execution)
        }

        fun processExecutionQueue() {
            while (!executionQueue.isEmpty()) {
                val execution = executionQueue.pop()
                runChild(execution)
            }
        }

        override fun <T> execute(callback: KotlinKernelHost.() -> T): T {
            return callback(ExecutionContext(sharedContext, displayHandler, executor, stackFrame.push()))
        }

        override fun declare(variables: Iterable<VariableDeclaration>) {
            val tempDeclarations = variables.joinToString(
                "\n",
                "object $TEMP_OBJECT_NAME {\n",
                "\n}\n$TEMP_OBJECT_NAME"
            ) {
                it.tempDeclaration
            }
            val result = execute(tempDeclarations).value as Any
            val resultClass = result::class
            val propertiesMap = resultClass.declaredMemberProperties.associateBy { it.name }

            val declarations = variables.joinToString("\n") {
                @Suppress("UNCHECKED_CAST")
                val prop = propertiesMap[it.name] as KMutableProperty1<Any, Any?>
                prop.set(result, it.value)
                it.declaration
            }
            execute(declarations)
        }

        companion object {
            private const val TEMP_OBJECT_NAME = "___temp_declarations"

            private val VariableDeclaration.mutabilityQualifier get() = if (isMutable) "var" else "val"
            private val VariableDeclaration.declaration get() = """$mutabilityQualifier `$name`: $type = $TEMP_OBJECT_NAME.`$name` as $type"""
            private val VariableDeclaration.tempDeclaration get() = """var `$name`: ${type.withNullability(true)} = null"""
        }
    }
}
