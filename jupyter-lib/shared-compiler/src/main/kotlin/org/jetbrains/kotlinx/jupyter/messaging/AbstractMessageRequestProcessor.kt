package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage

abstract class AbstractMessageRequestProcessor(
    rawIncomingMessage: RawMessage,
) : MessageRequestProcessor {
    protected val incomingMessage = rawIncomingMessage.toMessage()

    override fun processControlMessage() {
        when (val content = incomingMessage.content) {
            is InterruptRequest -> processInterruptRequest(content)
            is ShutdownRequest -> processShutdownRequest(content)
            else -> processUnknownControlMessage(content)
        }
    }

    override fun processShellMessage() {
        when (val content = incomingMessage.content) {
            is KernelInfoRequest -> processKernelInfoRequest(content)
            is UpdateClientMetadataRequest -> processUpdateClientMetadata(content)
            is HistoryRequest -> processHistoryRequest(content)

            // TODO: This request is deprecated since messaging protocol v.5.1,
            // remove it in future versions of kernel
            is ConnectRequest -> processConnectRequest(content)
            is ExecuteRequest -> processExecuteRequest(content)
            is CommInfoRequest -> processCommInfoRequest(content)
            is CommOpenMessage -> processCommOpen(content)
            is CommCloseMessage -> processCommClose(content)
            is CommMsgMessage -> processCommMsg(content)
            is CompleteRequest -> processCompleteRequest(content)
            is ListErrorsRequest -> processListErrorsRequest(content)
            is IsCompleteRequest -> processIsCompleteRequest(content)
            else -> processUnknownShellMessage(content)
        }
    }

    override fun processStdinMessage() {
        when (val content = incomingMessage.content) {
            is InputReply -> processInputReply(content)
            else -> processUnknownStdinMessage(content)
        }
    }

    protected abstract fun processIsCompleteRequest(content: IsCompleteRequest)

    protected abstract fun processListErrorsRequest(content: ListErrorsRequest)

    protected abstract fun processCompleteRequest(content: CompleteRequest)

    protected abstract fun processCommMsg(content: CommMsgMessage)

    protected abstract fun processCommClose(content: CommCloseMessage)

    protected abstract fun processCommOpen(content: CommOpenMessage)

    protected abstract fun processCommInfoRequest(content: CommInfoRequest)

    protected abstract fun processExecuteRequest(content: ExecuteRequest)

    protected abstract fun processConnectRequest(content: ConnectRequest)

    protected abstract fun processHistoryRequest(content: HistoryRequest)

    protected abstract fun processKernelInfoRequest(content: KernelInfoRequest)

    protected abstract fun processUpdateClientMetadata(content: UpdateClientMetadataRequest)

    protected abstract fun processUnknownShellMessage(content: MessageContent)

    protected abstract fun processShutdownRequest(content: ShutdownRequest)

    protected abstract fun processInterruptRequest(content: InterruptRequest)

    protected abstract fun processUnknownControlMessage(content: MessageContent)

    protected abstract fun processInputReply(content: InputReply)

    protected abstract fun processUnknownStdinMessage(content: MessageContent)
}
