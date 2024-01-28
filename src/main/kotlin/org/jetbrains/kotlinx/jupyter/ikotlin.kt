package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.rawMessageCallback
import org.jetbrains.kotlinx.jupyter.config.createRuntimeProperties
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutorImpl
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.getDefaultClasspathResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.JupyterBaseSockets
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacilityImpl
import org.jetbrains.kotlinx.jupyter.messaging.JupyterConnectionImpl
import org.jetbrains.kotlinx.jupyter.messaging.JupyterConnectionInternal
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProvider
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProviderImpl
import org.jetbrains.kotlinx.jupyter.messaging.MessageHandler
import org.jetbrains.kotlinx.jupyter.messaging.MessageHandlerImpl
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerImpl
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.repl.ReplConfig
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.repl.creating.DefaultReplComponentsProvider
import org.jetbrains.kotlinx.jupyter.repl.creating.createRepl
import org.jetbrains.kotlinx.jupyter.startup.KernelArgs
import org.jetbrains.kotlinx.jupyter.startup.getConfig
import java.io.File
import kotlin.concurrent.thread
import kotlin.script.experimental.jvm.util.classpathFromClassloader

val iKotlinClass: Class<*> = object {}::class.java.enclosingClass

private fun parseCommandLine(vararg args: String): KernelArgs {
    var cfgFile: File? = null
    var classpath: List<File>? = null
    var homeDir: File? = null
    var debugPort: Int? = null
    var clientType: String? = null
    var jvmTargetForSnippets: String? = null
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
            else -> {
                cfgFile?.let { throw IllegalArgumentException("config file already set to $it") }
                cfgFile = File(arg)
            }
        }
    }
    val cfgFileValue = cfgFile ?: throw IllegalArgumentException("config file is not provided")
    if (!cfgFileValue.exists() || !cfgFileValue.isFile) throw IllegalArgumentException("invalid config file $cfgFileValue")

    return KernelArgs(cfgFileValue, classpath ?: emptyList(), homeDir, debugPort, clientType, jvmTargetForSnippets)
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
        val libraryInfoProvider = getDefaultClasspathResolutionInfoProvider()
        val kernelConfig = kernelArgs.getConfig()
        val replConfig = ReplConfig.create(libraryInfoProvider, kernelArgs.homeDir)
        val runtimeProperties = createRuntimeProperties(kernelConfig)
        val replSettings = DefaultReplSettings(kernelConfig, replConfig, runtimeProperties)
        kernelServer(replSettings)
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
 * because EmptyResolutionInfoProvider is a Kotlin singleton object, and it takes a while to understand how to use it from Java code.
 */
@Suppress("unused")
fun embedKernel(cfgFile: File, resolutionInfoProvider: ResolutionInfoProvider?, scriptReceivers: List<Any>? = null) {
    val cp = System.getProperty("java.class.path").split(File.pathSeparator).toTypedArray().map { File(it) }

    val kernelConfig = KernelArgs(cfgFile, cp, null, null, null, null).getConfig()
    val replConfig = ReplConfig.create(
        resolutionInfoProvider ?: EmptyResolutionInfoProvider,
        null,
        true,
    )
    val replSettings = DefaultReplSettings(kernelConfig, replConfig, scriptReceivers = scriptReceivers.orEmpty())
    kernelServer(replSettings)
}

fun createMessageHandler(
    replSettings: DefaultReplSettings,
    socketManager: JupyterBaseSockets,
): MessageHandler {
    val messageFactoryProvider: MessageFactoryProvider = MessageFactoryProviderImpl()
    val communicationFacility: JupyterCommunicationFacility = JupyterCommunicationFacilityImpl(socketManager, messageFactoryProvider)

    val executor: JupyterExecutor = JupyterExecutorImpl()

    val commManager: CommManagerInternal = CommManagerImpl(communicationFacility)
    val repl = DefaultReplComponentsProvider(replSettings, communicationFacility, commManager).createRepl()
    return MessageHandlerImpl(repl, commManager, messageFactoryProvider, socketManager, executor)
}

fun kernelServer(replSettings: DefaultReplSettings) {
    val kernelConfig = replSettings.kernelConfig
    log.info("Starting server with config: $kernelConfig")

    JupyterConnectionImpl(kernelConfig).use { conn: JupyterConnectionInternal ->
        printClassPath()

        log.info("Begin listening for events")

        val socketManager = conn.socketManager
        val messageHandler = createMessageHandler(replSettings, socketManager)

        val mainThread = Thread.currentThread()

        fun socketLoop(
            interruptedMessage: String,
            vararg threadsToInterrupt: Thread,
            loopBody: () -> Unit,
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

        conn.addMessageCallback(
            rawMessageCallback(JupyterSocketType.CONTROL, null) { rawMessage ->
                messageHandler.handleMessage(JupyterSocketType.CONTROL, rawMessage)
            },
        )

        conn.addMessageCallback(
            rawMessageCallback(JupyterSocketType.SHELL, null) { rawMessage ->
                messageHandler.handleMessage(JupyterSocketType.SHELL, rawMessage)
            },
        )

        val controlThread = thread {
            socketLoop("Control: Interrupted", mainThread) {
                socketManager.control.runCallbacksOnMessage()
            }
        }

        val hbThread = thread {
            socketLoop("Heartbeat: Interrupted", mainThread) {
                socketManager.heartbeat.onData { send(it) }
            }
        }

        socketLoop("Main: Interrupted", controlThread, hbThread) {
            socketManager.shell.runCallbacksOnMessage()
        }

        try {
            controlThread.join()
            hbThread.join()
        } catch (_: InterruptedException) {
        }

        log.info("Shutdown server")
    }
}
