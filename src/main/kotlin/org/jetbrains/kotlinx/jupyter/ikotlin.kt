package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.KERNEL_LIBRARIES
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.getDefaultDirectoryResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.CommManagerImpl
import org.jetbrains.kotlinx.jupyter.messaging.controlMessagesHandler
import org.jetbrains.kotlinx.jupyter.messaging.shellMessagesHandler
import org.jetbrains.kotlinx.jupyter.repl.creating.DefaultReplFactory
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.script.experimental.jvm.util.classpathFromClassloader

val iKotlinClass: Class<*> = object {}::class.java.enclosingClass

data class KernelArgs(
    val cfgFile: File,
    val scriptClasspath: List<File>,
    val homeDir: File?,
    val debugPort: Int?,
) {
    fun parseParams(): KernelJupyterParams {
        return KernelJupyterParams.fromFile(cfgFile)
    }

    fun argsList(): List<String> {
        return mutableListOf<String>().apply {
            add(cfgFile.absolutePath)
            homeDir?.let { add("-home=${it.absolutePath}") }
            if (scriptClasspath.isNotEmpty()) {
                val classPathString = scriptClasspath.joinToString(File.pathSeparator) { it.absolutePath }
                add("-cp=$classPathString")
            }
            debugPort?.let { add("-debugPort=$it") }
        }
    }
}

private fun parseCommandLine(vararg args: String): KernelArgs {
    var cfgFile: File? = null
    var classpath: List<File>? = null
    var homeDir: File? = null
    var debugPort: Int? = null
    args.forEach { arg ->
        when {
            arg.startsWith("-cp=") || arg.startsWith("-classpath=") -> {
                classpath?.let {
                    throw IllegalArgumentException("classpath already set to ${it.joinToString(File.pathSeparator)}")
                }
                classpath = arg.substringAfter('=').split(File.pathSeparator).map { File(it) }
            }
            arg.startsWith("-home=") -> {
                homeDir = File(arg.substringAfter('='))
            }
            arg.startsWith("-debugPort=") -> {
                debugPort = arg.substringAfter('=').toInt()
            }
            else -> {
                cfgFile?.let { throw IllegalArgumentException("config file already set to $it") }
                cfgFile = File(arg)
            }
        }
    }
    val cfgFileValue = cfgFile ?: throw IllegalArgumentException("config file is not provided")
    if (!cfgFileValue.exists() || !cfgFileValue.isFile) throw IllegalArgumentException("invalid config file $cfgFileValue")

    return KernelArgs(cfgFileValue, classpath ?: emptyList(), homeDir, debugPort)
}

fun printClassPath() {
    val cl = ClassLoader.getSystemClassLoader()

    val cp = classpathFromClassloader(cl)

    if (cp != null) {
        log.info("Current classpath: " + cp.joinToString())
    }
}

fun main(vararg args: String) {
    try {
        log.info("Kernel args: " + args.joinToString { it })
        val kernelArgs = parseCommandLine(*args)
        val libraryPath = KERNEL_LIBRARIES.homeLibrariesDir(kernelArgs.homeDir)
        val libraryInfoProvider = getDefaultDirectoryResolutionInfoProvider(libraryPath)
        val kernelConfig = KernelConfig.create(libraryInfoProvider, kernelArgs)
        kernelServer(kernelConfig)
    } catch (e: Exception) {
        log.error("exception running kernel with args: \"${args.joinToString()}\"", e)
    }
}

/**
 * This function is to be run in projects which use kernel as a library,
 * so we don't have a big need in covering it with tests
 *
 * The expected use case for this function is embedding into a Java application that doesn't necessarily support extensions written in Kotlin
 * The signature of this function should thus be simple, and e.g. allow resolutionInfoProvider to be null instead of having to pass EmptyResolutionInfoProvider
 * because EmptyResolutionInfoProvider is a Kotlin singleton object and it takes a while to understand how to use it from Java code.
 */
@Suppress("unused")
fun embedKernel(cfgFile: File, resolutionInfoProvider: ResolutionInfoProvider?, scriptReceivers: List<Any>? = null) {
    val cp = System.getProperty("java.class.path").split(File.pathSeparator).toTypedArray().map { File(it) }

    val config = KernelConfig.create(
        resolutionInfoProvider ?: EmptyResolutionInfoProvider,
        KernelArgs(cfgFile, cp, null, null),
        true
    )
    kernelServer(config, scriptReceivers = scriptReceivers ?: emptyList())
}

fun kernelServer(config: KernelConfig, runtimeProperties: ReplRuntimeProperties = defaultRuntimeProperties, scriptReceivers: List<Any> = emptyList()) {
    log.info("Starting server with config: $config")

    JupyterConnectionImpl(config).use { conn ->

        printClassPath()

        log.info("Begin listening for events")

        val executionCount = AtomicLong(1)

        val commManager = CommManagerImpl(conn)
        val repl = DefaultReplFactory(config, runtimeProperties, scriptReceivers, conn, commManager).createRepl()

        val mainThread = Thread.currentThread()

        fun socketLoop(
            interruptedMessage: String,
            vararg threadsToInterrupt: Thread,
            loopBody: () -> Unit
        ) {
            while (true) {
                try {
                    loopBody()
                } catch (e: InterruptedException) {
                    log.debug(interruptedMessage)
                    threadsToInterrupt.forEach { it.interrupt() }
                    break
                }
            }
        }

        conn.control.onMessage { message ->
            controlMessagesHandler(message, repl)
        }

        conn.shell.onMessage { message ->
            conn.updateSessionInfo(message)
            shellMessagesHandler(message, repl, commManager, executionCount)
        }

        val controlThread = thread {
            socketLoop("Control: Interrupted", mainThread) {
                conn.control.runCallbacksOnMessage()
            }
        }

        val hbThread = thread {
            socketLoop("Heartbeat: Interrupted", mainThread) {
                conn.heartbeat.onData { send(it, 0) }
            }
        }

        socketLoop("Main: Interrupted", controlThread, hbThread) {
            conn.shell.runCallbacksOnMessage()
        }

        try {
            controlThread.join()
            hbThread.join()
        } catch (_: InterruptedException) {
        }

        log.info("Shutdown server")
    }
}
