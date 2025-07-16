package org.jetbrains.kotlinx.jupyter.execution

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.kotlinx.jupyter.exceptions.isInterruptedException
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class JupyterExecutorImpl(
    loggerFactory: KernelLoggerFactory,
) : JupyterExecutor {
    private val logger = loggerFactory.getLogger(this::class)

    private inner class Task<T : Any>(
        private val name: String,
        private val classLoader: ClassLoader,
        private val body: () -> T,
    ) {
        private val resultFuture = CompletableFuture<ExecutionResult<T>>()

        fun execute() {
            require(!resultFuture.isDone) {
                "Task $name was already executed"
            }

            val myThread = Thread.currentThread()

            myThread.name = name
            myThread.contextClassLoader = classLoader

            var execException: Throwable? = null
            val execRes: T? =
                try {
                    executionInProgress.set(true)
                    body()
                } catch (e: Throwable) {
                    execException = e
                    null
                } finally {
                    myThread.name = IDLE_EXECUTOR_NAME
                    executionInProgress.set(false)
                    if (Thread.interrupted()) {
                        logger.info("Clearing interrupted status")
                    }
                }

            val exception = execException

            val result =
                if (exception == null) {
                    ExecutionResult.Success(execRes!!)
                } else {
                    if (exception.isInterruptedException()) {
                        ExecutionResult.Interrupted
                    } else {
                        ExecutionResult.Failure(exception)
                    }
                }

            resultFuture.complete(result)
        }

        fun join(): ExecutionResult<T> = resultFuture.join()
    }

    private val tasksQueue = ArrayBlockingQueue<Task<*>>(MAX_QUEUED_TASKS)

    private val executionInProgress = AtomicBoolean(false)
    private var executorIsShuttingDown = false

    private val executorThread =
        thread(name = IDLE_EXECUTOR_NAME) {
            try {
                while (!executorIsShuttingDown) {
                    tasksQueue.take().execute()
                }
            } catch (_: InterruptedException) {
                // Ignore
            }
        }

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun <T : Any> runExecution(
        name: String,
        classLoader: ClassLoader?,
        body: () -> T,
    ): ExecutionResult<T> {
        val task =
            Task(
                name,
                classLoader ?: Thread.currentThread().contextClassLoader,
                body,
            )
        tasksQueue.put(task)
        return task.join()
    }

    /**
     * We cannot use [Thread.interrupt] here because we have no way
     * to control the code user executes. [Thread.interrupt] will do nothing for
     * the simple calculation (like `while (true) 1`). Consider replacing with
     * something smarter in the future.
     */
    override fun interruptExecution() {
        // We interrupt only current execution and don't clear the queue, it's intended
        logger.info("Stopping execution...")

        if (executionInProgress.get()) {
            val execution = executorThread
            val executionName = execution.name
            logger.info("Stopping $executionName...")

            // We hope that user implemented isInterrupted checks on their side
            execution.interrupt()
            logger.info("$executionName interrupted")
            Thread.sleep(100)

            if (execution.name == executionName) {
                // We're not lucky, execution is still in progress
                // Stopping it
                try {
                    @Suppress("DEPRECATION")
                    execution.stop()
                    logger.info("$executionName stopped")
                } catch (e: UnsupportedOperationException) {
                    logger.warn(
                        "We tried to stop $executionName thread, but it's not supported in the current version of JRE",
                        e,
                    )
                }
            }
        }
    }

    override fun launchJob(runnable: suspend CoroutineScope.() -> Unit) {
        coroutineScope.launch(block = runnable)
    }

    override fun close() {
        tasksQueue.clear()
        executorIsShuttingDown = true
        interruptExecution()
        executorThread.interrupt()
        coroutineScope.cancel("Jupyter executor was shut down")
    }

    companion object {
        private const val IDLE_EXECUTOR_NAME = "<idle>"
        private const val MAX_QUEUED_TASKS = 256
    }
}
