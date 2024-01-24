package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
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
