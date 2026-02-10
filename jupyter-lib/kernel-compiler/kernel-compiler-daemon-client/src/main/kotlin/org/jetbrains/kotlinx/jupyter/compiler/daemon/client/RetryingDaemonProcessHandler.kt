package org.jetbrains.kotlinx.jupyter.compiler.daemon.client

import kotlinx.rpc.krpc.client.InitializedKrpcClient
import kotlinx.rpc.krpc.rpcClientConfig
import kotlinx.rpc.krpc.serialization.cbor.cbor
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyAnnotation
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyResolutionResult
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.compiler.daemon.KernelCallbackService
import org.jetbrains.kotlinx.jupyter.compiler.daemon.transport.WebSocketClientKrpcTransport
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.InitHelper
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.mergeExceptions
import java.io.Closeable
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Wrapper that handles retrying [DaemonProcessHandler] creation on timeout failures.
 * Recreates the entire handler (shutting down and restarting the daemon process) on each retry.
 * Exposes the [rpcClient] for the daemon process.
 */
class RetryingDaemonProcessHandler(
    callbacks: KernelCallbacks,
    loggerFactory: KernelLoggerFactory,
    maxAttempts: Int = 3,
) : Closeable {
    private val logger = loggerFactory.getLogger(RetryingDaemonProcessHandler::class.java)

    private val clientTransport: WebSocketClientKrpcTransport
    private val initHelper: InitHelper

    init {
        var lastException: RuntimeException? = null
        lateinit var createdClientTransport: WebSocketClientKrpcTransport
        lateinit var attemptInitHelper: InitHelper

        for (attempt in 1..maxAttempts) {
            attemptInitHelper = InitHelper()
            try {
                val daemonPortLatch = CountDownLatch(1)
                var daemonPort: Int? = null

                attemptInitHelper.initialize {
                    DaemonProcessHandler(
                        callbackService =
                            KernelCallbackServiceImpl(callbacks, onDaemonPortAvailable = {
                                daemonPort = it
                                daemonPortLatch.countDown()
                            }),
                        loggerFactory = loggerFactory,
                    )
                }

                createdClientTransport =
                    attemptInitHelper.initialize {
                        daemonPortLatch.await(5, TimeUnit.SECONDS)
                        val daemonPort =
                            daemonPort
                                ?: throw DaemonTimeoutException("Daemon did not report its port within timeout")
                        WebSocketClientKrpcTransport(URI("ws://localhost:$daemonPort"))
                    }
                break
            } catch (e: RuntimeException) {
                lastException = e
                if (attempt < maxAttempts) {
                    logger.warn(
                        "Failed to create daemon handler (attempt $attempt/$maxAttempts): ${e.message}. Retrying...",
                    )
                }
                attemptInitHelper.closeAll()
            } catch (e: Throwable) {
                mergeExceptions {
                    addError(e) // we will throw this on mergeExceptions exit
                    attemptInitHelper.closeAll() // if any exceptions are thrown from here, they are added as suppressed
                }
            }
        }
        clientTransport = createdClientTransport
        initHelper = attemptInitHelper
    }

    val rpcClient =
        initHelper.initialize(
            initialize = {
                object : InitializedKrpcClient(
                    config =
                        rpcClientConfig {
                            connector {
                                callTimeout = 3.seconds
                                waitTimeout = 3.seconds
                            }
                            serialization { cbor() }
                        },
                    transport = clientTransport,
                ) {}
            },
            close = { it.close() },
        )

    override fun close(): Unit = initHelper.closeAll()
}

/**
 * Implementation of the kernel callback service that the daemon calls.
 */
private class KernelCallbackServiceImpl(
    private val callbacks: KernelCallbacks,
    private val onDaemonPortAvailable: (Int) -> Unit,
) : KernelCallbackService {
    override suspend fun reportDaemonPort(port: Int) {
        onDaemonPortAvailable(port)
    }

    override suspend fun resolveDependencies(annotations: List<DependencyAnnotation>): DependencyResolutionResult =
        callbacks.resolveDependencies(annotations)

    override suspend fun updatedClasspath(): List<String> = callbacks.updatedClasspath()
}
