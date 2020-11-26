package org.jetbrains.kotlin.jupyter

import org.jetbrains.kotlin.jupyter.libraries.LibraryFactory
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.script.experimental.jvm.util.classpathFromClassloader

val iKotlinClass: Class<*> = object {}::class.java.enclosingClass

data class KernelArgs(
    val cfgFile: File,
    val scriptClasspath: List<File>,
    val homeDir: File?
) {
    fun argsList(): List<String> {
        return mutableListOf<String>().apply {
            add(cfgFile.absolutePath)
            homeDir?.let { add("-home=${it.absolutePath}") }
            if (scriptClasspath.isNotEmpty()) {
                val classPathString = scriptClasspath.joinToString(File.pathSeparator) { it.absolutePath }
                add("-cp=$classPathString")
            }
        }
    }
}

private fun parseCommandLine(vararg args: String): KernelArgs {
    var cfgFile: File? = null
    var classpath: List<File>? = null
    var homeDir: File? = null
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
            else -> {
                cfgFile?.let { throw IllegalArgumentException("config file already set to $it") }
                cfgFile = File(arg)
            }
        }
    }
    val cfgFileValue = cfgFile ?: throw IllegalArgumentException("config file is not provided")
    if (!cfgFileValue.exists() || !cfgFileValue.isFile) throw IllegalArgumentException("invalid config file $cfgFileValue")

    return KernelArgs(cfgFileValue, classpath ?: emptyList(), homeDir)
}

fun printClassPath() {

    val cl = ClassLoader.getSystemClassLoader()

    val cp = classpathFromClassloader(cl)

    if (cp != null)
        log.info("Current classpath: " + cp.joinToString())
}

fun main(vararg args: String) {
    try {
        log.info("Kernel args: " + args.joinToString { it })
        val kernelArgs = parseCommandLine(*args)
        val libraryPath = (kernelArgs.homeDir ?: File("")).resolve(LibrariesDir)
        val libraryFactory = LibraryFactory.withDefaultDirectoryResolution(libraryPath)
        val kernelConfig = KernelConfig.fromArgs(kernelArgs, libraryFactory)
        kernelServer(kernelConfig)
    } catch (e: Exception) {
        log.error("exception running kernel with args: \"${args.joinToString()}\"", e)
    }
}

/**
 * This function is to be run in projects which use kernel as a library,
 * so we don't have a big need in covering it with tests
 */
@Suppress("unused")
fun embedKernel(cfgFile: File, libraryFactory: LibraryFactory?, scriptReceivers: List<Any>?) {
    val cp = System.getProperty("java.class.path").split(File.pathSeparator).toTypedArray().map { File(it) }
    val config = KernelConfig.fromConfig(
        KernelJupyterParams.fromFile(cfgFile),
        libraryFactory ?: LibraryFactory.EMPTY,
        cp,
        null,
        true
    )
    kernelServer(config, scriptReceivers = scriptReceivers ?: emptyList())
}

fun kernelServer(config: KernelConfig, runtimeProperties: ReplRuntimeProperties = defaultRuntimeProperties, scriptReceivers: List<Any> = emptyList()) {
    log.info("Starting server with config: $config")

    JupyterConnection(config).use { conn ->

        printClassPath()

        log.info("Begin listening for events")

        val executionCount = AtomicLong(1)

        val repl = ReplForJupyterImpl(config, runtimeProperties, scriptReceivers)

        val mainThread = Thread.currentThread()

        val controlThread = thread {
            while (true) {
                try {
                    conn.heartbeat.onData { send(it, 0) }
                    conn.control.onMessage { controlMessagesHandler(it, repl) }

                    Thread.sleep(config.pollingIntervalMillis)
                } catch (e: InterruptedException) {
                    log.debug("Control: Interrupted")
                    mainThread.interrupt()
                    break
                }
            }
        }

        while (true) {
            try {
                conn.shell.onMessage { message -> shellMessagesHandler(message, repl, executionCount) }

                Thread.sleep(config.pollingIntervalMillis)
            } catch (e: InterruptedException) {
                log.debug("Main: Interrupted")
                controlThread.interrupt()
                break
            }
        }

        try {
            controlThread.join()
        } catch (e: InterruptedException) {
        }

        log.info("Shutdown server")
    }
}
