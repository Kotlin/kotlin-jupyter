package org.jetbrains.kotlinx.jupyter.execution

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.kotlinx.jupyter.config.logger
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import java.io.Closeable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class JupyterExecutorImpl : JupyterExecutor, Closeable {
    private val currentExecutions: MutableSet<Thread> = Collections.newSetFromMap(ConcurrentHashMap())
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun <T : Any> runExecution(name: String, classLoader: ClassLoader?, body: () -> T): ExecutionResult<T> {
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

    override fun close() {
        interruptExecutions()
        coroutineScope.cancel("Jupyter executor was shut down")
    }

    companion object {
        val LOG = logger<JupyterExecutorImpl>()
    }
}
