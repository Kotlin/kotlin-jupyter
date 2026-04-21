package org.jetbrains.kotlinx.jupyter.compiler.daemon.client

import kotlinx.rpc.krpc.rpcServerConfig
import kotlinx.rpc.krpc.serialization.cbor.cbor
import kotlinx.rpc.krpc.server.KrpcServer
import kotlinx.rpc.registerService
import org.jetbrains.kotlinx.jupyter.compiler.daemon.KernelCallbackService
import org.jetbrains.kotlinx.jupyter.compiler.daemon.transport.WebSocketServerKrpcTransport
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.IdempotentCloser
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.InitHelper
import org.jetbrains.kotlinx.jupyter.protocol.startup.PortsGenerator
import org.jetbrains.kotlinx.jupyter.protocol.startup.create
import java.io.Closeable
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class DaemonTimeoutException(
    message: String,
) : RuntimeException(message)

/**
 * Starts a daemon process and a server to handle its callbacks.
 * [callbackService] is used to handle callbacks from the daemon,
 * including [KernelCallbackService.reportDaemonPort],
 * which the daemon calls to report its port as soon as it starts.
 */
class DaemonProcessHandler(
    callbackService: KernelCallbackService,
    loggerFactory: KernelLoggerFactory,
) : Closeable {
    private val logger = loggerFactory.getLogger(DaemonProcessHandler::class.java)

    private val initHelper = InitHelper()

    init {
        // Register shutdown hook to ensure daemon is killed when JVM exits
        Runtime.getRuntime().addShutdownHook(
            initHelper.initialize(
                initialize = { Thread(this::close) },
                close = { Runtime.getRuntime().removeShutdownHook(it) },
            ),
        )
    }

    init {
        val callbackPort: Int = PortsGenerator.create(32768, 65536).randomPort()

        logger.info("Starting daemon with callback port: $callbackPort")

        val callbackTransport: WebSocketServerKrpcTransport =
            initHelper.initialize {
                WebSocketServerKrpcTransport(callbackPort)
            }

        initHelper.initialize(
            initialize = {
                val krpcServerConfig =
                    rpcServerConfig {
                        connector {
                            callTimeout = 3.seconds
                            waitTimeout = 3.seconds
                        }
                        serialization { cbor() }
                    }

                object : KrpcServer(krpcServerConfig, callbackTransport) {}.apply {
                    registerService<KernelCallbackService> { callbackService }
                }
            },
            close = { it.close() },
        )

        logger.debug("Kernel callback server started on port $callbackPort")

        initHelper.initialize(
            initialize = {
                ProcessBuilder(
                    System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                    "-jar",
                    extractDaemonJar().absolutePath,
                    callbackPort.toString(),
                ).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .start()
            },
            close = {
                it.destroy()
                if (!it.waitFor(5, TimeUnit.SECONDS)) {
                    it.destroyForcibly()
                }
            },
        )
    }

    private val closer = IdempotentCloser { initHelper.closeAll() }

    /** Closes the daemon client, shutting down the daemon process and all connections. */
    override fun close() = closer.close()

    private fun extractDaemonJar(): File {
        // Extract the daemon JAR from resources (it's packaged with this module)
        val resourceStream =
            javaClass.getResourceAsStream("/compiler-daemon.jar")
                ?: throw RuntimeException("Could not find compiler-daemon.jar in resources. Please rebuild the project.")

        // Create a temporary file to hold the daemon JAR
        val tempFile = File.createTempFile("compiler-daemon-", ".jar")
        tempFile.deleteOnExit()

        resourceStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return tempFile
    }
}
