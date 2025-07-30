package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.CodeEvaluator
import org.jetbrains.kotlinx.jupyter.api.DEFAULT
import org.jetbrains.kotlinx.jupyter.api.EmbeddedKernelRunMode
import org.jetbrains.kotlinx.jupyter.api.KernelRunMode
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.api.StandaloneKernelRunMode
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.config.createRuntimeProperties
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutorImpl
import org.jetbrains.kotlinx.jupyter.libraries.DefaultResolutionInfoProviderFactory
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteRequest
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacilityImpl
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
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerSockets
import org.jetbrains.kotlinx.jupyter.protocol.api.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.startup.getConfig
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelArgs
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelArgumentsBuilder
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelConfig
import org.jetbrains.kotlinx.jupyter.repl.ReplConfig
import org.jetbrains.kotlinx.jupyter.repl.ResolutionInfoProviderFactory
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.repl.creating.DefaultReplComponentsProvider
import org.jetbrains.kotlinx.jupyter.repl.creating.createRepl
import org.jetbrains.kotlinx.jupyter.repl.embedded.NoOpInMemoryReplResultsHolder
import org.jetbrains.kotlinx.jupyter.startup.JupyterServerRunner
import org.jetbrains.kotlinx.jupyter.startup.KernelJupyterParamsSerializer
import org.jetbrains.kotlinx.jupyter.startup.parameters.KotlinKernelOwnParams
import org.jetbrains.kotlinx.jupyter.startup.parameters.KotlinKernelOwnParamsBuilder
import org.slf4j.Logger
import java.io.File
import kotlin.script.experimental.jvm.util.classpathFromClassloader

@PublishedApi
internal val iKotlinClass = object : Any() {}.javaClass.enclosingClass

fun parseCommandLine(vararg args: String): KernelArgs<KotlinKernelOwnParams> =
    KernelArgumentsBuilder(
        ownParamsBuilder = KotlinKernelOwnParamsBuilder(),
    ).parseArgs(args)

@PublishedApi
internal fun printClassPath(logger: Logger) {
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
        val kernelConfig = kernelArgs.getConfig(KernelJupyterParamsSerializer)
        val replSettings =
            createReplSettings(
                loggerFactory,
                StandaloneKernelRunMode,
                kernelConfig,
            )
        runServer(replSettings)
    } catch (e: Exception) {
        logger.error("exception running kernel with args: \"${args.joinToString()}\"", e)
    }
}

/**
 * This function is to be run in projects which use kernel as a library,
 * so we don't have a big need in covering it with tests
 *
 * The expected use case for this function is embedded into a Java application
 * that doesn't necessarily support extensions written in Kotlin.
 * The signature of this function should thus be simple,
 * and e.g., allow resolutionInfoProvider to be null instead of having to pass EmptyResolutionInfoProvider.
 * That's because EmptyResolutionInfoProvider is a Kotlin singleton object,
 * and it takes a while to understand how to use it from Java code.
 */
@Suppress("unused")
fun embedKernel(
    cfgFile: File,
    resolutionInfoProviderFactory: ResolutionInfoProviderFactory?,
    scriptReceivers: List<Any>? = null,
) {
    val scriptClasspath =
        System
            .getProperty("java.class.path")
            .split(File.pathSeparator)
            .toTypedArray()
            .map(::File)
    val kernelOwnParams =
        KotlinKernelOwnParams(
            scriptClasspath = scriptClasspath,
            homeDir = null,
            debugPort = null,
            clientType = null,
            jvmTargetForSnippets = null,
            replCompilerMode = ReplCompilerMode.DEFAULT,
            extraCompilerArguments = emptyList(),
        )
    val kernelConfig =
        KernelArgs(
            cfgFile = cfgFile,
            ownParams = kernelOwnParams,
        ).getConfig(KernelJupyterParamsSerializer)
    val replSettings =
        createReplSettings(
            DefaultKernelLoggerFactory,
            EmbeddedKernelRunMode,
            kernelConfig,
            resolutionInfoProviderFactory,
            scriptReceivers,
        )
    runServer(replSettings)
}

fun createReplSettings(
    loggerFactory: KernelLoggerFactory,
    kernelRunMode: KernelRunMode,
    kernelConfig: KernelConfig<KotlinKernelOwnParams>,
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

@PublishedApi
internal fun createMessageHandler(
    replSettings: DefaultReplSettings,
    socketManager: JupyterServerSockets,
): MessageHandlerImpl {
    val loggerFactory = replSettings.loggerFactory
    val messageFactoryProvider: MessageFactoryProvider = MessageFactoryProviderImpl()
    val communicationFacility: JupyterCommunicationFacility = JupyterCommunicationFacilityImpl(socketManager, messageFactoryProvider)

    val executor: JupyterExecutor = JupyterExecutorImpl(loggerFactory)

    val commManager: CommManagerInternal = CommManagerImpl(communicationFacility)
    val replProvider =
        DefaultReplComponentsProvider(
            replSettings,
            communicationFacility,
            commManager,
            NoOpInMemoryReplResultsHolder,
        )
    val repl = replProvider.createRepl()
    return MessageHandlerImpl(loggerFactory, repl, commManager, messageFactoryProvider, socketManager, executor)
}

@PublishedApi
internal fun initializeKernelSession(
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

fun runServer(replSettings: DefaultReplSettings) {
    val kernelConfig = replSettings.kernelConfig
    val loggerFactory = replSettings.loggerFactory
    val logger = loggerFactory.getLogger(iKotlinClass)
    val ports = kernelConfig.jupyterParams.ports
    val serverRunner =
        JupyterServerRunner.instances
            .find { it.canRun(ports) }
            ?: error("No server runner found for ports $ports")
    logger.info("Starting server with config: $kernelConfig (using ${serverRunner.javaClass.simpleName} server runner)")

    serverRunner.run(
        jupyterParams = kernelConfig.jupyterParams,
        loggerFactory = loggerFactory,
        setup = { sockets ->
            printClassPath(logger)

            logger.info("Begin listening for events")

            val messageHandler = createMessageHandler(replSettings, sockets)
            initializeKernelSession(messageHandler, replSettings)

            sockets.control.onRawMessage {
                messageHandler.handleMessage(JupyterSocketType.CONTROL, it)
            }

            sockets.shell.onRawMessage {
                messageHandler.handleMessage(JupyterSocketType.SHELL, it)
            }

            listOf(messageHandler)
        },
    )
}
