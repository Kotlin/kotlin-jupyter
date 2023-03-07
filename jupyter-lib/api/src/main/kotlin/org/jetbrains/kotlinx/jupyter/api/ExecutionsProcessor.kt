package org.jetbrains.kotlinx.jupyter.api

interface ExecutionsProcessor<T : Any> {
    fun register(execution: T) = register(execution, ProcessingPriority.DEFAULT)
    fun register(execution: T, priority: Int)
    fun registerAll(executions: Iterable<T>) {
        for (execution in executions) {
            register(execution)
        }
    }
    fun unregister(execution: T)
    fun unregisterAll()
    fun registeredExecutions(): Collection<T>
    fun registeredExecutionsWithPriority(): List<Pair<T, Int>>
}
