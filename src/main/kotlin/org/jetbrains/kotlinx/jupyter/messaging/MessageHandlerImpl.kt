package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import java.util.concurrent.atomic.AtomicLong

class MessageHandlerImpl(
    private val repl: ReplForJupyter,
    private val commManager: CommManagerInternal,
    private val messageFactoryProvider: MessageFactoryProvider,
    private val socketManager: JupyterBaseSockets,
    private val executor: JupyterExecutor,
) : AbstractMessageHandler() {
    private val executionCount = AtomicLong(1)

    override fun createProcessor(message: RawMessage): MessageRequestProcessor {
        return MessageRequestProcessorImpl(
            message,
            messageFactoryProvider,
            socketManager,
            commManager,
            executor,
            executionCount,
            repl,
        )
    }
}
