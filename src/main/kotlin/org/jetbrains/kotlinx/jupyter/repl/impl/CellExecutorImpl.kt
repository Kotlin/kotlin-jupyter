package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlinx.jupyter.DisplayHandler
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.HTML
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.VariableDeclaration
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.rethrowAsLibraryException
import org.jetbrains.kotlinx.jupyter.joinToLines
import org.jetbrains.kotlinx.jupyter.libraries.buildDependenciesInitCode
import org.jetbrains.kotlinx.jupyter.libraries.getDefinitions
import org.jetbrains.kotlinx.jupyter.log
import org.jetbrains.kotlinx.jupyter.putImplicitReceiver
import org.jetbrains.kotlinx.jupyter.repl.CellExecutor
import org.jetbrains.kotlinx.jupyter.repl.ExecutionStartedCallback
import org.jetbrains.kotlinx.jupyter.repl.InternalEvalResult
import java.util.LinkedList
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.withNullability

interface BaseKernelHost {
    fun <T> withHost(currentHost: KotlinKernelHost, callback: () -> T): T
}

internal class CellExecutorImpl(private val replContext: SharedReplContext) : CellExecutor {

    override fun execute(
        code: Code,
        displayHandler: DisplayHandler?,
        processVariables: Boolean,
        processAnnotations: Boolean,
        processMagics: Boolean,
        invokeAfterCallbacks: Boolean,
        callback: ExecutionStartedCallback?
    ): InternalEvalResult {
        with(replContext) {
            val context = ExecutionContext(replContext, displayHandler, this@CellExecutorImpl)

            log.debug("Executing code:\n$code")
            val preprocessedCode = if (processMagics) {
                val processedMagics = codePreprocessor.process(code, context)

                log.debug("Adding ${processedMagics.libraries.size} libraries")
                processedMagics.libraries.getDefinitions(notebook).forEach {
                    context.addLibrary(it)
                }

                processedMagics.code
            } else code

            if (preprocessedCode.isBlank()) {
                return InternalEvalResult(FieldValue(Unit, null), Unit)
            }

            val result = baseHost.withHost(context) {
                evaluator.eval(preprocessedCode) { internalId ->
                    if (callback != null) callback(internalId, preprocessedCode)
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
                librariesScanner.addLibrariesFromClassLoader(evaluator.lastClassLoader, context)
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
        return callback(ExecutionContext(replContext, null, this))
    }

    private class ExecutionContext(private val sharedContext: SharedReplContext, private val displayHandler: DisplayHandler?, private val executor: CellExecutor) :
        KotlinKernelHost, ExecutionHost {

        private val executionQueue = LinkedList<ExecutionCallback<*>>()

        private fun runChild(code: Code) {
            if (code.isNotBlank()) runChild(CodeExecution(code).toExecutionCallback())
        }

        private fun runChild(code: ExecutionCallback<*>) {
            execute(code)
        }

        override fun addLibrary(library: LibraryDefinition) {
            rethrowAsLibraryException(LibraryProblemPart.PREBUILT) {
                library.buildDependenciesInitCode()?.let { runChild(it) }
            }
            rethrowAsLibraryException(LibraryProblemPart.INIT) {
                library.init.forEach(::runChild)
            }
            library.renderers.mapNotNull(sharedContext.renderersProcessor::register).joinToLines().let(::runChild)
            library.converters.forEach(sharedContext.fieldsProcessor::register)
            library.classAnnotations.forEach(sharedContext.classAnnotationsProcessor::register)
            library.fileAnnotations.forEach(sharedContext.fileAnnotationsProcessor::register)
            sharedContext.afterCellExecution.addAll(library.afterCellExecution)
            sharedContext.codePreprocessor.addAll(library.codePreprocessors)

            val classLoader = sharedContext.evaluator.lastClassLoader
            rethrowAsLibraryException(LibraryProblemPart.RESOURCES) {
                library.resources.forEach {
                    val htmlText = sharedContext.resourcesProcessor.wrapLibrary(it, classLoader)
                    displayHandler?.handleDisplay(HTML(htmlText))
                }
            }

            library.initCell.filter { !sharedContext.beforeCellExecution.contains(it) }.let(sharedContext.beforeCellExecution::addAll)
            library.shutdown.filter { !sharedContext.shutdownCodes.contains(it) }.let(sharedContext.shutdownCodes::addAll)
        }

        override fun execute(code: Code) = executor.execute(code, displayHandler, processVariables = false, invokeAfterCallbacks = false).result

        override fun display(value: Any) {
            displayHandler?.handleDisplay(value)
        }

        override fun updateDisplay(value: Any, id: String?) {
            displayHandler?.handleUpdate(value, id)
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
            return callback(ExecutionContext(sharedContext, displayHandler, executor))
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

        override fun withReceiver(receiver: Any, type: KType) {
            sharedContext.implicitReceivers.putImplicitReceiver(receiver, type)
        }

        override fun removeReceiver(type: KType) {
            sharedContext.implicitReceivers.remove(type)
        }

        companion object {
            private const val TEMP_OBJECT_NAME = "___temp_declarations"

            private val VariableDeclaration.mutabilityQualifier get() = if (isMutable) "var" else "val"
            private val VariableDeclaration.declaration get() = """$mutabilityQualifier `$name`: $type = $TEMP_OBJECT_NAME.`$name` as $type"""
            private val VariableDeclaration.tempDeclaration get() = """var `$name`: ${type.withNullability(true)} = null"""
        }
    }
}
