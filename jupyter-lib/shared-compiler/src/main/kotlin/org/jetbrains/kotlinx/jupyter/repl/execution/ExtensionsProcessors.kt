package org.jetbrains.kotlinx.jupyter.repl.execution

import org.jetbrains.kotlinx.jupyter.api.AfterCellExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ExtensionsProcessor
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.rethrowAsLibraryException
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import org.jetbrains.kotlinx.jupyter.repl.ShutdownEvalResult
import org.jetbrains.kotlinx.jupyter.util.PriorityList

abstract class AbstractExtensionsProcessor<T : Any>(
    latterFirst: Boolean = false,
) : ExtensionsProcessor<T> {
    protected val extensions = PriorityList<T>(latterFirst = latterFirst)

    override fun register(
        extension: T,
        priority: Int,
    ) {
        extensions.addOrUpdatePriority(extension, priority)
    }

    override fun unregister(extension: T) {
        extensions.remove(extension)
    }

    override fun unregisterAll() {
        extensions.clear()
    }

    override fun registeredExtensions(): Collection<T> = extensions.elements()

    override fun registeredExtensionsWithPriority(): List<Pair<T, Int>> = extensions.elementsWithPriority()
}

class BeforeCellExecutionsProcessor : AbstractExtensionsProcessor<ExecutionCallback<*>>() {
    fun process(executor: CellExecutor) {
        extensions.forEach {
            rethrowAsLibraryException(LibraryProblemPart.BEFORE_CELL_CALLBACKS) {
                executor.execute(it)
            }
        }
    }
}

class AfterCellExecutionsProcessor(
    loggerFactory: KernelLoggerFactory,
) : AbstractExtensionsProcessor<AfterCellExecutionCallback>() {
    private val logger = loggerFactory.getLogger(this::class)

    fun process(
        host: KotlinKernelHost,
        snippetInstance: Any,
        result: FieldValue,
    ) {
        extensions.forEach {
            logger.catchAll {
                rethrowAsLibraryException(LibraryProblemPart.AFTER_CELL_CALLBACKS) {
                    it(host, snippetInstance, result)
                }
            }
        }
    }
}

class ShutdownExecutionsProcessor(
    loggerFactory: KernelLoggerFactory,
) : AbstractExtensionsProcessor<ExecutionCallback<*>>() {
    private val logger = loggerFactory.getLogger(this::class)

    fun process(executor: CellExecutor): List<ShutdownEvalResult> =
        extensions.map {
            val res =
                logger.catchAll {
                    rethrowAsLibraryException(LibraryProblemPart.SHUTDOWN) {
                        executor.execute(it)
                    }
                }
            ShutdownEvalResult(res)
        }
}
