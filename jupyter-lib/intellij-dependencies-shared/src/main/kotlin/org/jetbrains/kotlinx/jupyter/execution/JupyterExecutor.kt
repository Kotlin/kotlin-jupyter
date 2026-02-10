package org.jetbrains.kotlinx.jupyter.execution

import kotlinx.coroutines.CoroutineScope
import java.io.Closeable

interface JupyterExecutor : Closeable {
    fun <T : Any> runExecution(
        name: String,
        classLoader: ClassLoader? = null,
        body: () -> T,
    ): ExecutionResult<T>

    fun interruptExecution()

    fun launchJob(runnable: suspend CoroutineScope.() -> Unit)
}
