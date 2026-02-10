package org.jetbrains.kotlinx.jupyter.compiler.impl

import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import org.jetbrains.kotlinx.jupyter.compiler.proto.KernelCallbackServiceGrpcKt
import java.util.concurrent.TimeUnit

/**
 * Main entry point for the compiler daemon.
 *
 * Usage: CompilerDaemonMain <daemon-port> <kernel-callback-port>
 *
 * The daemon will:
 * 1. Connect to the kernel's callback service on kernel-callback-port
 * 2. Start its own gRPC server on daemon-port
 * 3. Wait for compilation requests from the kernel
 */
fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("Usage: CompilerDaemonMain <daemon-port> <kernel-callback-port>")
        System.exit(1)
    }

    val daemonPort =
        args[0].toIntOrNull() ?: run {
            System.err.println("Invalid daemon port: ${args[0]}")
            System.exit(1)
            return
        }

    val kernelCallbackPort =
        args[1].toIntOrNull() ?: run {
            System.err.println("Invalid kernel callback port: ${args[1]}")
            System.exit(1)
            return
        }

    println("Starting compiler daemon on port $daemonPort")
    println("Connecting to kernel callback service on port $kernelCallbackPort")

    val daemon = CompilerDaemon(daemonPort, kernelCallbackPort)
    daemon.start()
    daemon.blockUntilShutdown()
}

/**
 * The compiler daemon that runs a gRPC server for compilation requests.
 */
class CompilerDaemon(
    private val port: Int,
    private val kernelCallbackPort: Int,
) {
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
                .forPort(port)
                .addService(compilerService)
                .build()
                .start()

        println("Compiler daemon started on port $port")

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(
            Thread {
                System.err.println("*** Shutting down gRPC server since JVM is shutting down")
                this@CompilerDaemon.stop()
                System.err.println("*** Server shut down")
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
