package org.jetbrains.kotlinx.jupyter.compiler.impl

import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import org.jetbrains.kotlinx.jupyter.compiler.proto.KernelCallbackServiceGrpcKt
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.startup.PortsGenerator
import org.jetbrains.kotlinx.jupyter.protocol.startup.create
import java.util.concurrent.TimeUnit

/**
 * Main entry point for the compiler daemon.
 *
 * Usage: CompilerDaemonMain <kernel-callback-port>
 *
 * The daemon will:
 * 1. Connect to the kernel's callback service on kernel-callback-port
 * 2. Start its own gRPC server on a random available port (port 0)
 * 3. Report the actual port back to the kernel via ReportDaemonPort callback
 * 4. Wait for compilation requests from the kernel
 */
private val logger = DefaultKernelLoggerFactory.getLogger("CompilerDaemonMain")

fun main(args: Array<String>) {
    if (args.size != 1) {
        System.err.println("Usage: CompilerDaemonMain <kernel-callback-port>")
        System.exit(1)
    }

    val kernelCallbackPort =
        args[0].toIntOrNull() ?: run {
            System.err.println("Invalid kernel callback port: ${args[0]}")
            System.exit(1)
            return
        }

    logger.debug("Connecting to kernel callback service on port {}", kernelCallbackPort)

    val daemon = CompilerDaemon(kernelCallbackPort)
    daemon.start()
    daemon.blockUntilShutdown()
}

/**
 * The compiler daemon that runs a gRPC server for compilation requests.
 */
class CompilerDaemon(
    private val kernelCallbackPort: Int,
) {
    private val logger = DefaultKernelLoggerFactory.getLogger(CompilerDaemon::class.java)
    
    private var server: Server? = null

    fun start() {
        // Create channel to kernel's callback service
        val callbackChannel =
            ManagedChannelBuilder
                .forAddress("localhost", kernelCallbackPort)
                .usePlaintext()
                .build()

        val callbackStub = KernelCallbackServiceGrpcKt.KernelCallbackServiceCoroutineStub(callbackChannel)

        // Create and start the compiler service
        val compilerService = DaemonCompilerServiceImpl(callbackStub)

        server =
            ServerBuilder
                .forPort(PortsGenerator.create(32768, 65536).randomPort()) // Let OS pick an available port
                .addService(compilerService)
                .build()
                .start()

        val actualPort = server!!.port
        logger.debug("Compiler daemon started on port {}", actualPort)

        // Report the actual port back to the kernel
        kotlinx.coroutines.runBlocking {
            val request = org.jetbrains.kotlinx.jupyter.compiler.proto.ReportDaemonPortRequest
                .newBuilder()
                .setPort(actualPort)
                .build()
            callbackStub.reportDaemonPort(request)
            logger.debug("Reported port {} to kernel", actualPort)
        }

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(
            Thread {
                logger.info("Shutting down gRPC server since JVM is shutting down")
                this@CompilerDaemon.stop()
                logger.info("Server shut down")
            },
        )
    }

    fun stop() {
        server?.shutdown()?.awaitTermination(30, TimeUnit.SECONDS)
    }

    fun blockUntilShutdown() {
        server?.awaitTermination()
    }
}
