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
import org.jetbrains.kotlinx.jupyter.protocol.startup.PortsGenerator
import org.jetbrains.kotlinx.jupyter.protocol.startup.create
import java.net.URI
import kotlin.concurrent.thread
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

    val daemon = CompilerDaemon(kernelCallbackPort)
    daemon.start()
    daemon.blockUntilShutdown()
}

/**
 * The compiler daemon that runs a server over WebSocket for compilation requests.
 */
class CompilerDaemon(
    private val kernelCallbackPort: Int,
) {
    private val logger = DefaultKernelLoggerFactory.getLogger(CompilerDaemon::class.java)

    private var serverTransport: WebSocketServerKrpcTransport? = null
    private var clientTransport: WebSocketClientKrpcTransport? = null

    @Volatile
    private var running = true
    private var server: KrpcServer? = null

    fun start() {
        // Create transport to kernel's callback service
        logger.info("Connecting to kernel callback on port: $kernelCallbackPort")
        clientTransport = WebSocketClientKrpcTransport(URI("ws://localhost:$kernelCallbackPort"))

        val clientConfig =
            rpcClientConfig {
                serialization { cbor() }
            }

        // Create client to call kernel callbacks
        val callbackClient = object : InitializedKrpcClient(clientConfig, clientTransport!!) {}
        val callbackService: KernelCallbackService = callbackClient.withService()

        // Create and start the compiler service server
        val serverPort = PortsGenerator.create(32768, 65536).randomPort()
        serverTransport = WebSocketServerKrpcTransport(serverPort)

        val compilerService = DaemonCompilerServiceImpl(callbackService)

        val serverConfig =
            rpcServerConfig {
                serialization { cbor() }
            }

        server =
            object : KrpcServer(serverConfig, serverTransport!!) {}.apply {
                registerService<JupyterCompilerDaemonService> { compilerService }
            }

        logger.debug("Compiler daemon started on port {}", serverPort)

        // Report the actual port back to the kernel
        runBlocking {
            callbackService.reportDaemonPort(serverPort)
            logger.debug("Reported port {} to kernel", serverPort)
        }

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(
            Thread {
                logger.info("Shutting down server since JVM is shutting down")
                this@CompilerDaemon.stop()
                logger.info("Server shut down")
            },
        )
    }

    private fun stop() {
        running = false
        server?.close()
        serverTransport?.close()
        clientTransport?.close()
    }

    fun blockUntilShutdown() {
        // Start a thread to monitor stdin - exit if stdin closes (parent process died)
        thread(name = "stdin-monitor") {
            try {
                logger.debug("Started stdin monitor thread")
                // Read from stdin indefinitely - blocks until stdin closes
                while (System.`in`.read() != -1 && running) {
                    // Continue reading and discarding input
                }
                logger.info("stdin closed, parent process likely terminated - shutting down daemon")
                stop()
                exitProcess(0)
            } catch (e: Exception) {
                logger.error("Error in stdin monitor thread: {}", e.message, e)
                stop()
                exitProcess(1)
            }
        }

        serverTransport?.coroutineContext?.job?.let {
            runBlocking { it.join() }
        }
    }
}
