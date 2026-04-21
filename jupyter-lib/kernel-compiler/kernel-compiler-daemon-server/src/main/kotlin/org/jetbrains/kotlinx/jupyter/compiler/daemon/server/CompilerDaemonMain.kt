package org.jetbrains.kotlinx.jupyter.compiler.daemon.server

import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.rpc.krpc.client.InitializedKrpcClient
import kotlinx.rpc.krpc.rpcClientConfig
import kotlinx.rpc.krpc.rpcServerConfig
import kotlinx.rpc.krpc.serialization.cbor.cbor
import kotlinx.rpc.krpc.server.KrpcServer
import kotlinx.rpc.registerService
import kotlinx.rpc.withService
import org.jetbrains.kotlinx.jupyter.compiler.daemon.JupyterCompilerDaemonService
import org.jetbrains.kotlinx.jupyter.compiler.daemon.KernelCallbackService
import org.jetbrains.kotlinx.jupyter.compiler.daemon.transport.WebSocketClientKrpcTransport
import org.jetbrains.kotlinx.jupyter.compiler.daemon.transport.WebSocketServerKrpcTransport
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.IdempotentCloser
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.InitHelper
import org.jetbrains.kotlinx.jupyter.protocol.startup.PortsGenerator
import org.jetbrains.kotlinx.jupyter.protocol.startup.create
import java.io.Closeable
import java.net.URI
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

/**
 * Main entry point for the compiler daemon.
 *
 * Usage: CompilerDaemonMain <kernel-callback-port>
 *
 * The daemon will:
 * 1. Connect to the kernel's callback service on kernel-callback-port via WebSocket
 * 2. Start its own server on a random available port
 * 3. Report the actual port back to the kernel via ReportDaemonPort callback
 * 4. Wait for compilation requests from the kernel
 */
private val logger = DefaultKernelLoggerFactory.getLogger("CompilerDaemonMain")

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage: CompilerDaemonMain <kernel-callback-port>")
        exitProcess(1)
    }

    val kernelCallbackPort =
        args[0].toIntOrNull() ?: run {
            System.err.println("Invalid kernel callback port: ${args[0]}")
            exitProcess(1)
        }

    logger.debug("Starting compiler daemon, connecting to kernel callback on port {}", kernelCallbackPort)

    CompilerDaemon(kernelCallbackPort).use {
        it.start()
        it.blockUntilShutdown()
    }
}

/**
 * The compiler daemon that runs a server over WebSocket for compilation requests.
 */
class CompilerDaemon(
    private val kernelCallbackPort: Int,
) : Closeable {
    private val logger = DefaultKernelLoggerFactory.getLogger(CompilerDaemon::class.java)

    private var serverCoroutineContext: CoroutineContext? = null

    private val initHelper = InitHelper()
    private val closer = IdempotentCloser { initHelper.closeAll() }

    override fun close(): Unit = closer.close()

    fun start() {
        // Create transport to kernel's callback service
        logger.info("Connecting to kernel callback on port: $kernelCallbackPort")
        val clientTransport =
            initHelper.initialize {
                WebSocketClientKrpcTransport(URI("ws://localhost:$kernelCallbackPort"))
            }

        // Create client to call kernel callbacks
        val callbackClient =
            initHelper.initialize(
                initialize = {
                    val clientConfig =
                        rpcClientConfig {
                            serialization { cbor() }
                        }

                    object : InitializedKrpcClient(clientConfig, clientTransport) {}
                },
                close = { it.close() },
            )

        // Create and start the compiler service server
        val serverPort = PortsGenerator.create(32768, 65536).randomPort()
        val serverTransport =
            initHelper.initialize {
                WebSocketServerKrpcTransport(serverPort)
            }
        serverCoroutineContext = serverTransport.coroutineContext

        val callbackService: KernelCallbackService = callbackClient.withService()
        val compilerService = initHelper.initialize { DaemonCompilerServiceImpl(callbackService) }

        initHelper.initialize(
            initialize = {
                val serverConfig =
                    rpcServerConfig {
                        serialization { cbor() }
                    }

                object : KrpcServer(serverConfig, serverTransport) {}.apply {
                    registerService<JupyterCompilerDaemonService> { compilerService }
                }
            },
            close = { it.close() },
        )

        logger.debug("Compiler daemon started on port {}", serverPort)

        // Report the actual port back to the kernel
        runBlocking {
            callbackService.reportDaemonPort(serverPort)
            logger.debug("Reported port {} to kernel", serverPort)
        }

        Runtime.getRuntime().addShutdownHook(
            initHelper.initialize(
                initialize = {
                    Thread {
                        logger.info("Shutting down server since JVM is shutting down")
                        closer.close()
                        logger.info("Server shut down")
                    }
                },
                close = { Runtime.getRuntime().removeShutdownHook(it) },
            ),
        )
    }

    fun blockUntilShutdown() {
        // Start a thread to monitor stdin - exit if stdin closes (parent process died)
        thread(name = "stdin-monitor", isDaemon = true) {
            try {
                logger.debug("Started stdin monitor thread")
                // Read from stdin indefinitely - blocks until stdin closes
                while (System.`in`.read() != -1) {
                    // Continue reading and discarding input
                }
                logger.info("stdin closed, parent process likely terminated - shutting down daemon")
                closer.close()
                exitProcess(0)
            } catch (e: Exception) {
                logger.error("Error in stdin monitor thread: {}", e.message, e)
                closer.close()
                exitProcess(1)
            }
        }

        serverCoroutineContext?.job?.let {
            runBlocking { it.join() }
        }
    }
}
