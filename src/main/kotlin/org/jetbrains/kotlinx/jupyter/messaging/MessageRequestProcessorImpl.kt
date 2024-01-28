package org.jetbrains.kotlinx.jupyter.messaging

import ch.qos.logback.classic.Level
import org.jetbrains.kotlinx.jupyter.LoggingManagement
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import java.util.concurrent.atomic.AtomicLong

open class MessageRequestProcessorImpl(
    rawIncomingMessage: RawMessage,
    messageFactoryProvider: MessageFactoryProvider,
    socketManager: JupyterBaseSockets,
    commManager: CommManagerInternal,
    executor: JupyterExecutor,
    executionCount: AtomicLong,
    repl: ReplForJupyter,
) : IdeCompatibleMessageRequestProcessor(
    rawIncomingMessage,
    messageFactoryProvider,
    socketManager,
    commManager,
    executor,
    executionCount,
    repl,
),
    JupyterCommunicationFacility {

    override fun processIsCompleteRequest(content: IsCompleteRequest) {
        // We are in console mode, so switch off all the loggers
        if (LoggingManagement.mainLoggerLevel() != Level.OFF) LoggingManagement.disableLogging()

        val resStr = if (looksLikeReplCommand(content.code)) "complete" else {
            val result = try {
                val check = repl.checkComplete(content.code)
                when {
                    check.isComplete -> "complete"
                    else -> "incomplete"
                }
            } catch (ex: ReplCompilerException) {
                "invalid"
            }
            result
        }
        socketManager.shell.sendMessage(
            messageFactory.makeReplyMessage(MessageType.IS_COMPLETE_REPLY, content = IsCompleteReply(resStr)),
        )
    }
}
