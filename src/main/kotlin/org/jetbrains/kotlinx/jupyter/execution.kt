package org.jetbrains.kotlinx.jupyter

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.kotlinx.jupyter.config.logger
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import java.lang.UnsupportedOperationException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

sealed interface ExecutionResult<out T> {
    class Success<out T>(val result: T) : ExecutionResult<T>
    class Failure(val throwable: Throwable) : ExecutionResult<Nothing>
    data object Interrupted : ExecutionResult<Nothing>
}

interface JupyterExecutor {
    fun <T> runExecution(name: String, classLoader: ClassLoader? = null, body: () -> T): ExecutionResult<T>
    fun interruptExecutions()

    fun launchJob(runnable: suspend CoroutineScope.() -> Unit)
}

class JupyterExecutorImpl : JupyterExecutor {
    private val currentExecutions: MutableSet<Thread> = Collections.newSetFromMap(ConcurrentHashMap())
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun <T> runExecution(name: String, classLoader: ClassLoader?, body: () -> T): ExecutionResult<T> {
        var execRes: T? = null
        var execException: Throwable? = null
        val execThread = thread(
            name = name,
            contextClassLoader = classLoader ?: Thread.currentThread().contextClassLoader,
        ) {
            try {
                execRes = body()
            } catch (e: Throwable) {
                execException = e
            }
        }
        currentExecutions.add(execThread)
        execThread.join()
        currentExecutions.remove(execThread)

        val exception = execException

        return if (exception == null) {
            ExecutionResult.Success(execRes!!)
        } else {
            val isInterrupted = exception is ThreadDeath ||
                (exception is ReplException && exception.cause is ThreadDeath)
            if (isInterrupted) ExecutionResult.Interrupted
            else ExecutionResult.Failure(exception)
        }
    }

    /**
     * We cannot use [Thread.interrupt] here because we have no way
     * to control the code user executes. [Thread.interrupt] will do nothing for
     * the simple calculation (like `while (true) 1`). Consider replacing with
     * something more smart in the future.
     */
    override fun interruptExecutions() {
        LOG.info("Stopping ${currentExecutions.size} executions...")
        while (currentExecutions.isNotEmpty()) {
            val execution = currentExecutions.firstOrNull() ?: break
            val executionName = execution.name
            LOG.info("Stopping $executionName...")

            // We hope that user implemented isInterrupted checks on their side
            execution.interrupt()
            LOG.info("$executionName interrupted")

            try {
                @Suppress("DEPRECATION")
                execution.stop()
                LOG.info("$executionName stopped")
            } catch (e: UnsupportedOperationException) {
                LOG.warn("We tried to stop $executionName thread, but it's not supported in the current version of JRE", e)
            }

            currentExecutions.remove(execution)
        }
    }

    override fun launchJob(runnable: suspend CoroutineScope.() -> Unit) {
        coroutineScope.launch(block = runnable)
    }

    companion object {
        val LOG = logger<JupyterExecutorImpl>()
    }
}
