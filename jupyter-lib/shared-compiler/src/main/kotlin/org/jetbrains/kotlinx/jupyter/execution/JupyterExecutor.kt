package org.jetbrains.kotlinx.jupyter.execution

import kotlinx.coroutines.CoroutineScope

interface JupyterExecutor {
    fun <T : Any> runExecution(name: String, classLoader: ClassLoader? = null, body: () -> T): ExecutionResult<T>
    fun interruptExecutions()

    fun launchJob(runnable: suspend CoroutineScope.() -> Unit)
}
