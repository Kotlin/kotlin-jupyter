package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.DEFAULT
import org.jetbrains.kotlinx.jupyter.api.EmbeddedKernelRunMode
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.KernelRunMode
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.api.StandaloneKernelRunMode
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.config.createRuntimeProperties
import org.jetbrains.kotlinx.jupyter.libraries.DefaultResolutionInfoProviderFactory
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.JupyterZmqSocketManagerImpl
import org.jetbrains.kotlinx.jupyter.repl.ReplConfig
import org.jetbrains.kotlinx.jupyter.repl.ResolutionInfoProviderFactory
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.ws.runWebSocketServer
import org.jetbrains.kotlinx.jupyter.startup.KernelArgs
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.startup.WsKernelPorts
import org.jetbrains.kotlinx.jupyter.startup.ZmqKernelPorts
import org.jetbrains.kotlinx.jupyter.startup.getConfig
import java.io.File

val cliLauncherClass: Class<*> = object {}::class.java.enclosingClass

private fun parseCommandLine(vararg args: String): KernelArgs {
    var cfgFile: File? = null
    var classpath: List<File>? = null
    var homeDir: File? = null
    var debugPort: Int? = null
    var clientType: String? = null
    var jvmTargetForSnippets: String? = null
    var replCompilerMode: ReplCompilerMode = ReplCompilerMode.DEFAULT
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
            arg.startsWith("-client=") -> {
                clientType = arg.substringAfter('=')
            }
            arg.startsWith("-jvmTarget") -> {
                jvmTargetForSnippets = arg.substringAfter('=')
            }
            arg.startsWith("-replCompilerMode=") -> {
                val userMode = arg.substringAfter('=')
                replCompilerMode = ReplCompilerMode.entries.find {
                    it.name == userMode
                } ?: throw IllegalArgumentException("Invalid replCompilerMode: $userMode")
            }
            else -> {
                cfgFile?.let { throw IllegalArgumentException("config file already set to $it") }
                cfgFile = File(arg)
            }
        }
    }
    val cfgFileValue = cfgFile ?: throw IllegalArgumentException("config file is not provided")
    if (!cfgFileValue.exists() || !cfgFileValue.isFile) throw IllegalArgumentException("invalid config file $cfgFileValue")

    return KernelArgs(cfgFileValue, classpath ?: emptyList(), homeDir, debugPort, clientType, jvmTargetForSnippets, replCompilerMode)
}

fun main(vararg args: String) {
    val loggerFactory = DefaultKernelLoggerFactory
    val logger = loggerFactory.getLogger(cliLauncherClass)
    try {
        logger.info("Kernel args: " + args.joinToString { it })
        val kernelArgs = parseCommandLine(*args)
        val kernelConfig = kernelArgs.getConfig()
        val replSettings =
            createReplSettings(
                loggerFactory,
                StandaloneKernelRunMode,
                kernelConfig,
            )
        when (kernelConfig.ports) {
            is ZmqKernelPorts -> runZmqServer(replSettings)
            is WsKernelPorts -> runWebSocketServer(replSettings)
            else -> error("Unknown kernel ports type: ${kernelConfig.ports}")
        }
    } catch (e: Exception) {
        logger.error("exception running kernel with args: \"${args.joinToString()}\"", e)
    }
}

/**
 * This function is to be run in projects which use kernel as a library,
 * so we don't have a big need in covering it with tests
 *
 * The expected use case for this function is embedded into a Java application that doesn't necessarily support extensions written in Kotlin
 * The signature of this function should thus be simple, and e.g., allow resolutionInfoProvider to be null instead of having to pass EmptyResolutionInfoProvider
 * because EmptyResolutionInfoProvider is a Kotlin singleton object, and it takes a while to understand how to use it from Java code.
 */
@Suppress("unused")
fun embedKernel(
    cfgFile: File,
    resolutionInfoProviderFactory: ResolutionInfoProviderFactory?,
    scriptReceivers: List<Any>? = null,
) {
    val scriptClasspath = System.getProperty("java.class.path").split(File.pathSeparator).toTypedArray().map {
        File(it)
    }
    val kernelConfig =
        KernelArgs(
            cfgFile = cfgFile,
            scriptClasspath = scriptClasspath,
            homeDir = null,
            debugPort = null,
            clientType = null,
            jvmTargetForSnippets = null,
            replCompilerMode = ReplCompilerMode.DEFAULT,
        ).getConfig()
    val replSettings =
        createReplSettings(
            DefaultKernelLoggerFactory,
            EmbeddedKernelRunMode,
            kernelConfig,
            resolutionInfoProviderFactory,
            scriptReceivers,
        )
    runZmqServer(replSettings)
}

fun createReplSettings(
    loggerFactory: KernelLoggerFactory,
    kernelRunMode: KernelRunMode,
    kernelConfig: KernelConfig,
    resolutionInfoProviderFactory: ResolutionInfoProviderFactory? = DefaultResolutionInfoProviderFactory,
    scriptReceivers: List<Any>? = null,
): DefaultReplSettings {
    val myResolutionInfoProviderFactory =
        resolutionInfoProviderFactory
            ?: ResolutionInfoProviderFactory { httpUtil, _ ->
                EmptyResolutionInfoProvider(httpUtil.libraryInfoCache)
            }

    val replConfig =
        ReplConfig.create(
            myResolutionInfoProviderFactory,
            loggerFactory,
            homeDir = kernelConfig.homeDir,
            kernelRunMode = kernelRunMode,
            scriptReceivers = scriptReceivers,
        )

    val runtimeProperties = createRuntimeProperties(kernelConfig)
    val replSettings =
        DefaultReplSettings(
            kernelConfig,
            replConfig,
            loggerFactory,
            runtimeProperties,
        )
    return replSettings
}

fun runZmqServer(replSettings: DefaultReplSettings) {
    runServer(replSettings, ::JupyterZmqSocketManagerImpl)
}
