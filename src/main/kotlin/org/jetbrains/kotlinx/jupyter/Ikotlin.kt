package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.CodeEvaluator
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutorImpl
import org.jetbrains.kotlinx.jupyter.logging.ReplComponentsProviderWithLogbackManager
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteRequest
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacilityImpl
import org.jetbrains.kotlinx.jupyter.messaging.JupyterServerSockets
import org.jetbrains.kotlinx.jupyter.messaging.JupyterSocketManager
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
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.repl.creating.DefaultReplComponentsProvider
import org.jetbrains.kotlinx.jupyter.repl.creating.createRepl
import org.jetbrains.kotlinx.jupyter.repl.embedded.NoOpInMemoryReplResultsHolder
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.slf4j.Logger
import kotlin.collections.joinToString
import kotlin.script.experimental.jvm.util.classpathFromClassloader

@PublishedApi
internal val iKotlinClass = object : Any() {}.javaClass.enclosingClass

@PublishedApi
internal fun printClassPath(logger: Logger) {
    val cl = ClassLoader.getSystemClassLoader()

    val cp = classpathFromClassloader(cl)

    if (cp != null) {
        logger.info("Current classpath: " + cp.joinToString())
    }
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
        ).let { defaultProvider ->
            ReplComponentsProviderWithLogbackManager(defaultProvider)
        }
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

inline fun runServer(
    replSettings: DefaultReplSettings,
    createSocketManager: (KernelLoggerFactory, KernelConfig) -> JupyterSocketManager,
) {
    val kernelConfig = replSettings.kernelConfig
    val loggerFactory = replSettings.loggerFactory
    val logger = loggerFactory.getLogger(iKotlinClass)
    logger.info("Starting server with config: $kernelConfig")

    createSocketManager(loggerFactory, kernelConfig).use { socketManager ->
        printClassPath(logger)

        logger.info("Begin listening for events")

        val messageHandler = createMessageHandler(replSettings, socketManager.sockets)
        initializeKernelSession(messageHandler, replSettings)

        socketManager.sockets.control.onRawMessage {
            messageHandler.handleMessage(JupyterSocketType.CONTROL, it)
        }

        socketManager.sockets.shell.onRawMessage {
            messageHandler.handleMessage(JupyterSocketType.SHELL, it)
        }

        try {
            socketManager.listen()
        } finally {
            logger.info("Server is stopped")
            messageHandler.close()
        }
    }
}