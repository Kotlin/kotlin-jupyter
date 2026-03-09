package org.jetbrains.kotlinx.jupyter.compiler.daemon

import io.grpc.ManagedChannel
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import java.io.Closeable

class DaemonTimeoutException(
    message: String,
) : RuntimeException(message)

/**
 * Wrapper that handles retrying DaemonProcessHandler creation on timeout failures.
 * Recreates the entire handler (shutting down and restarting the daemon process) on each retry.
 */
class RetryingDaemonProcessHandler(
    callbackServiceFactory: CallbackServiceFactory,
    loggerFactory: KernelLoggerFactory,
    maxAttempts: Int = 3,
) : Closeable {
    private val logger = loggerFactory.getLogger(RetryingDaemonProcessHandler::class.java)

    val handler: DaemonProcessHandler

    init {
        var lastException: DaemonTimeoutException? = null
        var createdHandler: DaemonProcessHandler? = null

        for (attempt in 1..maxAttempts) {
            try {
                createdHandler = DaemonProcessHandler(callbackServiceFactory, loggerFactory)
                break
            } catch (e: DaemonTimeoutException) {
                lastException = e
                if (attempt < maxAttempts) {
                    logger.warn(
                        "Failed to create daemon handler (attempt $attempt/$maxAttempts): ${e.message}. Retrying...",
                    )
                }
            }
        }

        handler = createdHandler ?: throw RuntimeException(
            "Failed to create daemon handler after $maxAttempts attempts",
            lastException,
        )
    }

    val channel: ManagedChannel get() = handler.channel

    override fun close() {
        handler.close()
    }
}
