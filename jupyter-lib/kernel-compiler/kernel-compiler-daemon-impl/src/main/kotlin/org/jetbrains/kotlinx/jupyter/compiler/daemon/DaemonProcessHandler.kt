package org.jetbrains.kotlinx.jupyter.compiler.daemon

import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.catchAllIndependentlyAndMerge
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.tryFinally
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.tryWithCleanup
import org.jetbrains.kotlinx.jupyter.protocol.startup.PortsGenerator
import org.jetbrains.kotlinx.jupyter.protocol.startup.create
import java.io.Closeable
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

fun interface DaemonPortCallback {
    fun reportDaemonPort(port: Int)
}

fun interface CallbackServiceFactory {
    fun createCallbackService(reportDaemonPort: DaemonPortCallback): BindableService
}

/** Handles daemon process lifecycle, and exposes [channel] to communicate with it. */
class DaemonProcessHandler(
    callbackServiceFactory: CallbackServiceFactory,
    loggerFactory: KernelLoggerFactory,
) : Closeable {
    private val logger = loggerFactory.getLogger(DaemonProcessHandler::class.java)

    @Volatile private var daemonPort: Int? = null
    private val daemonPortLatch = CountDownLatch(1)

    private val closeHandlers = mutableListOf<() -> Unit>()

    // Callback server for daemon to call back to kernel
    private val callbackPort: Int = PortsGenerator.create(32768, 65536).randomPort()
    private val callbackServer: Server =
        NettyServerBuilder
            .forPort(callbackPort)
            .addService(
                callbackServiceFactory.createCallbackService {
                    daemonPort = it
                    daemonPortLatch.countDown()
                },
            ).build()
            .start()

    init {
        closeHandlers.add {
            callbackServer.shutdown()
            if (!callbackServer.awaitTermination(5, TimeUnit.SECONDS)) {
                callbackServer.shutdownNow()
            }
        }

        logger.debug("Kernel callback server started on port {}", callbackPort)
    }

    private val daemonProcess: Process =
        tryWithCleanup(
            action = {
                ProcessBuilder(
                    System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                    "-jar",
                    extractDaemonJar().absolutePath,
                    callbackPort.toString(),
                ).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .start()
            },
            onErrorHandlers = closeHandlers,
        )

    init {
        closeHandlers.add {
            daemonProcess.destroy()
            if (!daemonProcess.waitFor(5, TimeUnit.SECONDS)) {
                daemonProcess.destroyForcibly()
            }
        }
    }

    val channel: ManagedChannel =
        tryWithCleanup(
            action = {
                logger.debug("Waiting for daemon to report its port...")
                daemonPortLatch.await(10, TimeUnit.SECONDS)
                val actualDaemonPort = daemonPort ?: throw RuntimeException("Daemon did not report its port within timeout")
                logger.debug("Daemon reported port: {}", actualDaemonPort)

                // Connect to daemon
                NettyChannelBuilder
                    .forAddress("localhost", actualDaemonPort)
                    .usePlaintext()
                    .build()
            },
            onErrorHandlers = closeHandlers,
        )

    init {
        closeHandlers.add {
            channel.shutdown()
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow()
            }
        }
        // Register shutdown hook to ensure daemon is killed when JVM exits
        Runtime.getRuntime().addShutdownHook(Thread(this::close))
    }

    private val isClosedLatch = CountDownLatch(1)
    private val startedClosing = AtomicBoolean(false)

    /**
     * Closes the daemon client, shutting down the daemon process and all connections.
     * This method is idempotent and safe to call multiple times.
     */
    override fun close() {
        if (startedClosing.getAndSet(true)) {
            isClosedLatch.await()
            return
        }
        tryFinally(
            action = { catchAllIndependentlyAndMerge(*closeHandlers.toTypedArray()) },
            finally = { isClosedLatch.countDown() },
        )
        logger.debug("Compiler daemon shut down successfully")
    }

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
