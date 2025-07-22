package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerSockets
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter

open class MessageRequestProcessorImpl(
    rawIncomingMessage: RawMessage,
    messageFactoryProvider: MessageFactoryProvider,
    socketManager: JupyterServerSockets,
    commManager: CommManagerInternal,
    executor: JupyterExecutor,
    executionCounter: ExecutionCounter,
    loggerFactory: KernelLoggerFactory,
    repl: ReplForJupyter,
) : IdeCompatibleMessageRequestProcessor(
        rawIncomingMessage,
        messageFactoryProvider,
        socketManager,
        commManager,
        executor,
        executionCounter,
        loggerFactory,
        repl,
    ),
    JupyterCommunicationFacility {
    override fun processIsCompleteRequest(content: IsCompleteRequest) {
        // We are in console mode, so switch off all the loggers
        val loggingManager = repl.loggingManager
        if (loggingManager.isLoggingEnabled()) loggingManager.disableLogging()

        val resStr =
            if (looksLikeReplCommand(content.code)) {
                "complete"
            } else {
                val result =
                    try {
                        val check = repl.checkComplete(content.code)
                        when {
                            check.isComplete -> "complete"
                            else -> "incomplete"
                        }
                    } catch (_: ReplCompilerException) {
                        "invalid"
                    }
                result
            }
        socketManager.shell.sendMessage(
            messageFactory.makeReplyMessage(MessageType.IS_COMPLETE_REPLY, content = IsCompleteReply(resStr)),
        )
    }
}
