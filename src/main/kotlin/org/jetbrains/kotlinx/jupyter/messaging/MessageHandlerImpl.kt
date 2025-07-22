package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.closeIfPossible
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerSockets
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import java.io.Closeable

class MessageHandlerImpl(
    private val loggerFactory: KernelLoggerFactory,
    val repl: ReplForJupyter,
    private val commManager: CommManagerInternal,
    private val messageFactoryProvider: MessageFactoryProvider,
    private val socketManager: JupyterServerSockets,
    private val executor: JupyterExecutor,
) : AbstractMessageHandler(),
    Closeable {
    private val executionCounter = ExecutionCounter(1)

    override fun createProcessor(message: RawMessage): MessageRequestProcessor =
        MessageRequestProcessorImpl(
            message,
            messageFactoryProvider,
            socketManager,
            commManager,
            executor,
            executionCounter,
            loggerFactory,
            repl,
        )

    override fun close() {
        repl.closeIfPossible()
    }
}
