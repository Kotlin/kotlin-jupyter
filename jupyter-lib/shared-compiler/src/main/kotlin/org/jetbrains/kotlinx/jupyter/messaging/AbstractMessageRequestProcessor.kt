package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage

abstract class AbstractMessageRequestProcessor(
    rawIncomingMessage: RawMessage,
) : MessageRequestProcessor {
    protected val incomingMessage = rawIncomingMessage.toMessage()

    override fun processControlMessage(message: RawMessage) {
        when (val content = incomingMessage.content) {
            is InterruptRequest -> processInterruptRequest(content)
            is ShutdownRequest -> processShutdownRequest(content)
            else -> processUnknownControlMessage(content)
        }
    }

    override fun processShellMessage(message: RawMessage) {
        when (val content = incomingMessage.content) {
            is KernelInfoRequest -> processKernelInfoRequest(content)
            is HistoryRequest -> processHistoryRequest(content)

            // TODO: This request is deprecated since messaging protocol v.5.1,
            // remove it in future versions of kernel
            is ConnectRequest -> processConnectRequest(content)
            is ExecuteRequest -> processExecuteRequest(content)
            is CommInfoRequest -> processCommInfoRequest(content)
            is CommOpen -> processCommOpen(content)
            is CommClose -> processCommClose(content)
            is CommMsg -> processCommMsg(content)
            is CompleteRequest -> processCompleteRequest(content)
            is ListErrorsRequest -> processListErrorsRequest(content)
            is IsCompleteRequest -> processIsCompleteRequest(content)
            else -> processUnknownShellMessage(content)
        }
    }

    protected abstract fun processIsCompleteRequest(content: IsCompleteRequest)
    protected abstract fun processListErrorsRequest(content: ListErrorsRequest)
    protected abstract fun processCompleteRequest(content: CompleteRequest)
    protected abstract fun processCommMsg(content: CommMsg)
    protected abstract fun processCommClose(content: CommClose)
    protected abstract fun processCommOpen(content: CommOpen)
    protected abstract fun processCommInfoRequest(content: CommInfoRequest)
    protected abstract fun processExecuteRequest(content: ExecuteRequest)
    protected abstract fun processConnectRequest(content: ConnectRequest)
    protected abstract fun processHistoryRequest(content: HistoryRequest)
    protected abstract fun processKernelInfoRequest(content: KernelInfoRequest)
    protected abstract fun processUnknownShellMessage(content: MessageContent)

    protected abstract fun processShutdownRequest(content: ShutdownRequest)
    protected abstract fun processInterruptRequest(content: InterruptRequest)
    protected abstract fun processUnknownControlMessage(content: MessageContent)
}
