package org.jetbrains.kotlinx.jupyter.repl
import org.jetbrains.kotlinx.jupyter.DisplayHandler
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.HTML
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.Execution
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.joinToLines
import org.jetbrains.kotlinx.jupyter.libraries.buildDependenciesInitCode
import org.jetbrains.kotlinx.jupyter.libraries.getDefinitions
import org.jetbrains.kotlinx.jupyter.log
import java.util.LinkedList

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
        callback: ExecutionStartedCallback?
    ): InternalEvalResult {
        with(replContext) {
            val context = ExecutionContext(replContext, displayHandler, this@CellExecutorImpl)

            val preprocessedCode = if (processMagics) {
                val processedMagics = magicsProcessor.processMagics(code)

                processedMagics.libraries.getDefinitions(notebook).forEach {
                    context.addLibrary(it)
                }

                processedMagics.code
            } else code

            if (preprocessedCode.isBlank()) {
                return InternalEvalResult(FieldValue(null, null), null)
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

            context.processExecutionQueue()

            return result
        }
    }

    override fun <T> execute(callback: KotlinKernelHost.() -> T): T {
        return callback(ExecutionContext(replContext, null, this))
    }

    private class ExecutionContext(private val sharedContext: SharedReplContext, private val displayHandler: DisplayHandler?, private val executor: CellExecutor) :
        KotlinKernelHost, ExecutionHost {

        private val executionQueue = LinkedList<Execution<*>>()

        private fun runChild(code: Code) {
            if (code.isNotBlank()) runChild(CodeExecution(code))
        }

        private fun runChild(code: Execution<*>) {
            code.execute(this)
        }

        override fun addLibrary(library: LibraryDefinition) {
            library.buildDependenciesInitCode()?.let { runChild(it) }
            library.init.forEach(::runChild)
            library.renderers.mapNotNull(sharedContext.typeRenderersProcessor::register).joinToLines().let(::runChild)
            library.converters.forEach(sharedContext.fieldsProcessor::register)
            library.classAnnotations.forEach(sharedContext.classAnnotationsProcessor::register)
            library.fileAnnotations.forEach(sharedContext.fileAnnotationsProcessor::register)

            val classLoader = sharedContext.evaluator.lastClassLoader
            library.resources.forEach {
                val htmlText = sharedContext.resourcesProcessor.wrapLibrary(it, classLoader)
                displayHandler?.handleDisplay(HTML(htmlText))
            }

            library.initCell.filter { !sharedContext.initCellCodes.contains(it) }.let(sharedContext.initCellCodes::addAll)
            library.shutdown.filter { !sharedContext.shutdownCodes.contains(it) }.let(sharedContext.shutdownCodes::addAll)
        }

        override fun execute(code: Code) = executor.execute(code, displayHandler, processVariables = false).field

        override fun display(value: Any) {
            displayHandler?.handleDisplay(value)
        }

        override fun updateDisplay(value: Any, id: String?) {
            displayHandler?.handleUpdate(value, id)
        }

        override fun scheduleExecution(execution: Execution<*>) {
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
    }
}
