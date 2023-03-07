package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlinx.jupyter.api.AfterCellExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ExecutionsProcessor
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.rethrowAsLibraryException
import org.jetbrains.kotlinx.jupyter.log
import org.jetbrains.kotlinx.jupyter.repl.CellExecutor
import org.jetbrains.kotlinx.jupyter.repl.ShutdownEvalResult
import org.jetbrains.kotlinx.jupyter.util.PriorityList

abstract class AbstractExecutionsProcessor<T : Any> : ExecutionsProcessor<T> {
    protected val executions = PriorityList<T>(latterFirst = false)

    override fun register(execution: T, priority: Int) {
        executions.addOrUpdatePriority(execution, priority)
    }

    override fun unregister(execution: T) {
        executions.remove(execution)
    }

    override fun unregisterAll() {
        executions.clear()
    }

    override fun registeredExecutions(): Collection<T> {
        return executions.elements()
    }

    override fun registeredExecutionsWithPriority(): List<Pair<T, Int>> {
        return executions.elementsWithPriority()
    }
}

class BeforeCellExecutionsProcessor : AbstractExecutionsProcessor<ExecutionCallback<*>>() {
    fun process(executor: CellExecutor) {
        executions.forEach {
            rethrowAsLibraryException(LibraryProblemPart.BEFORE_CELL_CALLBACKS) {
                executor.execute(it)
            }
        }
    }
}

class AfterCellExecutionsProcessor : AbstractExecutionsProcessor<AfterCellExecutionCallback>() {
    fun process(host: KotlinKernelHost, snippetInstance: Any, result: FieldValue) {
        executions.forEach {
            log.catchAll {
                rethrowAsLibraryException(LibraryProblemPart.AFTER_CELL_CALLBACKS) {
                    it(host, snippetInstance, result)
                }
            }
        }
    }
}

class ShutdownExecutionsProcessor : AbstractExecutionsProcessor<ExecutionCallback<*>>() {
    fun process(executor: CellExecutor): List<ShutdownEvalResult> {
        return executions.map {
            val res = log.catchAll {
                rethrowAsLibraryException(LibraryProblemPart.SHUTDOWN) {
                    executor.execute(it)
                }
            }
            ShutdownEvalResult(res)
        }
    }
}
