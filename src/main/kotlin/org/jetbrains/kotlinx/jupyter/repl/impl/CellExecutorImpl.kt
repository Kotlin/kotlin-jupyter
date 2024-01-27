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
import org.jetbrains.kotlinx.jupyter.api.libraries.KernelRepository
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.libraryDefinition
import org.jetbrains.kotlinx.jupyter.config.JupyterCompilingOptions
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.config.currentKotlinVersion
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.exceptions.rethrowAsLibraryException
import org.jetbrains.kotlinx.jupyter.joinToLines
import org.jetbrains.kotlinx.jupyter.libraries.buildDependenciesInitCode
import org.jetbrains.kotlinx.jupyter.libraries.getDefinitions
import org.jetbrains.kotlinx.jupyter.log
import org.jetbrains.kotlinx.jupyter.repl.InternalEvalResult
import org.jetbrains.kotlinx.jupyter.repl.SharedReplContext
import org.jetbrains.kotlinx.jupyter.repl.execution.CellExecutor
import org.jetbrains.kotlinx.jupyter.repl.execution.ExecutionStackFrame
import org.jetbrains.kotlinx.jupyter.repl.execution.ExecutorWorkflowListener
import org.jetbrains.kotlinx.jupyter.util.accepts
import java.util.*
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.withNullability

internal class CellExecutorImpl(private val replContext: SharedReplContext) : CellExecutor {

    override fun execute(
        code: Code,
        processVariables: Boolean,
        processAnnotations: Boolean,
        processMagics: Boolean,
        invokeAfterCallbacks: Boolean,
        isUserCode: Boolean,
        currentCellId: Int,
        stackFrame: ExecutionStackFrame?,
        executorWorkflowListener: ExecutorWorkflowListener?,
    ): InternalEvalResult {
        with(replContext) {
            val context = ExecutionContext(replContext, this@CellExecutorImpl, stackFrame.push())

            log.debug("Executing code:\n$code")
            val preprocessedCode = if (processMagics) {
                val processedMagics = codePreprocessor.process(code, context)

                log.debug("Adding ${processedMagics.libraries.size} libraries")
                val libraries = processedMagics.libraries.getDefinitions(notebook)

                for (library in libraries) {
                    context.addLibrary(library)
                }

                processedMagics.code
            } else code
            executorWorkflowListener?.codePreprocessed(preprocessedCode)

            if (preprocessedCode.isBlank()) {
                return InternalEvalResult(FieldValue(Unit, null), Unit)
            }

            val result = baseHost.withHost(context) {
                try {
                    evaluator.eval(
                        preprocessedCode,
                        JupyterCompilingOptions(currentCellId, isUserCode),
                        executorWorkflowListener,
                    )
                } catch (e: ReplException) {
                    if (e.cause is ThreadDeath) {
                        rethrowAsLibraryException(LibraryProblemPart.INTERRUPTION_CALLBACKS) {
                            interruptionCallbacksProcessor.runCallbacks()
                        }
                    }
                    throw e
                }
            }
            var newResultField: FieldValue? = null
            val snippetClass = evaluator.lastKClass

            if (processVariables) {
                log.catchAll {
                    fieldsProcessor.process(context)?.let { newResultField = it }
                }
            }

            if (processAnnotations) {
                log.catchAll {
                    classAnnotationsProcessor.process(snippetClass, context)
                }
            }

            // TODO: scan classloader only when new classpath was added
            log.catchAll {
                librariesScanner.addLibrariesFromClassLoader(
                    evaluator.lastClassLoader,
                    context,
                    notebook,
                    stackFrame.libraryOptions,
                )
            }

            log.catchAll {
                rethrowAsLibraryException(LibraryProblemPart.COLOR_SCHEME_CHANGE_CALLBACKS) {
                    colorSchemeChangeCallbacksProcessor.runCallbacks()
                }
            }

            if (invokeAfterCallbacks) {
                afterCellExecutionsProcessor.process(context, result.scriptInstance, result.result)
            }

            context.processExecutionQueue()

            return newResultField?.let { field -> result.copy(result = field) } ?: result
        }
    }

    override fun <T> execute(callback: KotlinKernelHost.() -> T): T {
        return callback(ExecutionContext(replContext, this, null.push()))
    }

    private class ExecutionContext(
        private val sharedContext: SharedReplContext,
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

        private fun doAddLibraries(libraries: Collection<LibraryDefinition>) {
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
                library.textRenderers.forEach { sharedContext.textRenderersProcessor.register(it.renderer, it.priority) }
                library.throwableRenderers.forEach(sharedContext.throwableRenderersProcessor::register)
                library.converters.forEach(sharedContext.fieldsProcessor::register)
                library.classAnnotations.forEach(sharedContext.classAnnotationsProcessor::register)
                library.fileAnnotations.forEach(sharedContext.fileAnnotationsProcessor::register)
                library.interruptionCallbacks.forEach(sharedContext.interruptionCallbacksProcessor::register)
                library.colorSchemeChangedCallbacks.forEach(sharedContext.colorSchemeChangeCallbacksProcessor::register)
                sharedContext.afterCellExecutionsProcessor.registerAll(library.afterCellExecution)
                sharedContext.codePreprocessor.registerAll(library.codePreprocessors)

                val classLoader = sharedContext.evaluator.lastClassLoader
                rethrowAsLibraryException(LibraryProblemPart.RESOURCES) {
                    library.resources.forEach {
                        val htmlText = sharedContext.resourcesProcessor.wrapLibrary(it, classLoader)
                        sharedContext.displayHandler.handleDisplay(HTML(htmlText), this)
                    }
                }

                sharedContext.beforeCellExecutionsProcessor.registerAll(library.initCell)
                sharedContext.shutdownExecutionsProcessor.registerAll(library.shutdown)
            }
        }

        override fun addLibraries(libraries: Collection<LibraryDefinition>) {
            if (libraries.isEmpty()) return
            stackFrame.libraries.addAll(libraries)
            try {
                doAddLibraries(libraries)
            } finally {
                for (lib in libraries) {
                    stackFrame.libraries.removeLast()
                }
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
                    it.repositories = repositories.map(::KernelRepository)
                    it.dependencies = artifacts.map { name -> "org.jetbrains.kotlin:kotlin-$name:$kotlinVersion" }
                },
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

        override fun execute(code: Code) = executor.execute(
            code,
            processVariables = false,
            invokeAfterCallbacks = false,
            stackFrame = stackFrame,
        ).result

        override fun display(value: Any, id: String?) {
            sharedContext.displayHandler.handleDisplay(value, this, id)
        }

        override fun updateDisplay(value: Any, id: String?) {
            sharedContext.displayHandler.handleUpdate(value, this, id)
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
            return callback(ExecutionContext(sharedContext, executor, stackFrame.push()))
        }

        override fun declare(variables: Iterable<VariableDeclaration>) {
            val tempDeclarations = variables.joinToString(
                "\n",
                "object $TEMP_OBJECT_NAME {\n",
                "\n}\n$TEMP_OBJECT_NAME",
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

        override val lastClassLoader: ClassLoader
            get() = sharedContext.evaluator.lastClassLoader

        companion object {
            private const val TEMP_OBJECT_NAME = "___temp_declarations"

            private val VariableDeclaration.mutabilityQualifier get() = if (isMutable) "var" else "val"
            private val VariableDeclaration.declaration get() = """$mutabilityQualifier `$name`: $type = $TEMP_OBJECT_NAME.`$name` as $type"""
            private val VariableDeclaration.tempDeclaration get() = """var `$name`: ${type.withNullability(true)} = null"""
        }
    }
}
