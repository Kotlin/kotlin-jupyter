package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.CodeEvaluator
import org.jetbrains.kotlinx.jupyter.api.EmbeddedKernelRunMode
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.KernelRunMode
import org.jetbrains.kotlinx.jupyter.api.StandaloneKernelRunMode
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterConnection
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.rawMessageCallback
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.config.createRuntimeProperties
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutorImpl
import org.jetbrains.kotlinx.jupyter.libraries.DefaultResolutionInfoProviderFactory
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteRequest
import org.jetbrains.kotlinx.jupyter.messaging.JupyterBaseSockets
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacilityImpl
import org.jetbrains.kotlinx.jupyter.messaging.JupyterConnectionImpl
import org.jetbrains.kotlinx.jupyter.messaging.JupyterConnectionInternal
import org.jetbrains.kotlinx.jupyter.messaging.Message
import org.jetbrains.kotlinx.jupyter.messaging.MessageData
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProvider
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProviderImpl
import org.jetbrains.kotlinx.jupyter.messaging.MessageHandlerImpl
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerImpl
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.messaging.makeHeader
import org.jetbrains.kotlinx.jupyter.messaging.toRawMessage
import org.jetbrains.kotlinx.jupyter.repl.ReplConfig
import org.jetbrains.kotlinx.jupyter.repl.ResolutionInfoProviderFactory
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.repl.creating.DefaultReplComponentsProvider
import org.jetbrains.kotlinx.jupyter.repl.creating.createRepl
import org.jetbrains.kotlinx.jupyter.repl.embedded.NoOpInMemoryReplResultsHolder
import org.jetbrains.kotlinx.jupyter.startup.DEFAULT
import org.jetbrains.kotlinx.jupyter.startup.KernelArgs
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.startup.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.startup.getConfig
import org.slf4j.Logger
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

fun printClassPath(logger: Logger) {
    val cl = ClassLoader.getSystemClassLoader()

    val cp = classpathFromClassloader(cl)

    if (cp != null) {
        logger.info("Current classpath: " + cp.joinToString())
    }
}

fun main(vararg args: String) {
    val loggerFactory = DefaultKernelLoggerFactory
    val logger = loggerFactory.getLogger(iKotlinClass)
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
        startZmqServer(replSettings)
    } catch (e: Exception) {
        logger.error("exception running kernel with args: \"${args.joinToString()}\"", e)
    }
}

/**
 * This function is to be run in projects which use kernel as a library,
 * so we don't have a big need in covering it with tests
 *
 * The expected use case for this function is embedded into a Java application that doesn't necessarily support extensions written in Kotlin
 * The signature of this function should thus be simple, and e.g. allow resolutionInfoProvider to be null instead of having to pass EmptyResolutionInfoProvider
 * because EmptyResolutionInfoProvider is a Kotlin singleton object, and it takes a while to understand how to use it from Java code.
 */
@Suppress("unused")
fun embedKernel(
    cfgFile: File,
    resolutionInfoProviderFactory: ResolutionInfoProviderFactory?,
    scriptReceivers: List<Any>? = null,
) {
    val scriptClasspath = System.getProperty("java.class.path").split(File.pathSeparator).toTypedArray().map { File(it) }
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
    startZmqServer(replSettings)
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

fun createMessageHandler(
    replSettings: DefaultReplSettings,
    socketManager: JupyterBaseSockets,
): MessageHandlerImpl {
    val loggerFactory = replSettings.loggerFactory
    val messageFactoryProvider: MessageFactoryProvider = MessageFactoryProviderImpl()
    val communicationFacility: JupyterCommunicationFacility = JupyterCommunicationFacilityImpl(socketManager, messageFactoryProvider)

    val executor: JupyterExecutor = JupyterExecutorImpl(loggerFactory)

    val commManager: CommManagerInternal = CommManagerImpl(communicationFacility)
    val repl = DefaultReplComponentsProvider(replSettings, communicationFacility, commManager, NoOpInMemoryReplResultsHolder).createRepl()
    return MessageHandlerImpl(loggerFactory, repl, commManager, messageFactoryProvider, socketManager, executor)
}

fun startZmqServer(replSettings: DefaultReplSettings) {
    val kernelConfig = replSettings.kernelConfig
    val loggerFactory = replSettings.loggerFactory
    val logger = loggerFactory.getLogger(iKotlinClass)
    logger.info("Starting server with config: $kernelConfig")

    JupyterConnectionImpl(loggerFactory, kernelConfig).use { conn: JupyterConnectionInternal ->
        printClassPath(logger)

        logger.info("Begin listening for events")

        val socketManager = conn.socketManager
        val messageHandler = createMessageHandler(replSettings, socketManager)
        initializeKernelSession(messageHandler, replSettings)

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
                    logger.debug(interruptedMessage)
                    threadsToInterrupt.forEach { it.interrupt() }
                    break
                }
            }
        }

        fun JupyterConnection.addMessageCallbackForSocket(socketType: JupyterSocketType) {
            addMessageCallback(
                rawMessageCallback(socketType, null) { rawMessage ->
                    messageHandler.handleMessage(socketType, rawMessage)
                },
            )
        }

        conn.addMessageCallbackForSocket(JupyterSocketType.CONTROL)
        conn.addMessageCallbackForSocket(JupyterSocketType.SHELL)

        val controlThread =
            thread {
                socketLoop("Control: Interrupted", mainThread) {
                    socketManager.control.runCallbacksOnMessage()
                }
            }

        val hbThread =
            thread {
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
        } finally {
            messageHandler.closeIfPossible()
        }

        logger.info("Server is stopped")
    }
}

private fun initializeKernelSession(
    messageHandler: MessageHandlerImpl,
    replSettings: DefaultReplSettings,
) {
    val codeEvaluator =
        CodeEvaluator { code ->
            val executeRequest =
                ExecuteRequest(
                    code,
                    storeHistory = false,
                )
            val messageData =
                MessageData(
                    header = makeHeader(MessageType.EXECUTE_REQUEST),
                    content = executeRequest,
                )
            val message =
                Message(
                    data = messageData,
                )
            messageHandler.handleMessage(
                JupyterSocketType.SHELL,
                message.toRawMessage(),
            )
        }

    replSettings.replConfig.kernelRunMode.initializeSession(
        messageHandler.repl.notebook,
        codeEvaluator,
    )
}
