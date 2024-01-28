package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion.Companion.toMaybeUnspecifiedString
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.commands.runCommand
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.config.KernelStreams
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
import org.jetbrains.kotlinx.jupyter.config.currentKotlinVersion
import org.jetbrains.kotlinx.jupyter.config.logger
import org.jetbrains.kotlinx.jupyter.config.notebookLanguageInfo
import org.jetbrains.kotlinx.jupyter.exceptions.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.execution.ExecutionResult
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.protocol.CapturingOutputStream
import org.jetbrains.kotlinx.jupyter.protocol.DisabledStdinInputStream
import org.jetbrains.kotlinx.jupyter.protocol.StdinInputStream
import org.jetbrains.kotlinx.jupyter.protocol.protocolVersion
import org.jetbrains.kotlinx.jupyter.repl.EvalRequestData
import org.jetbrains.kotlinx.jupyter.repl.EvalResultEx
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.repl.toDisplayResult
import org.jetbrains.kotlinx.jupyter.util.EMPTY
import java.io.InputStream
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

@Suppress("MemberVisibilityCanBePrivate")
open class IdeCompatibleMessageRequestProcessor(
    rawIncomingMessage: RawMessage,
    messageFactoryProvider: MessageFactoryProvider,
    final override val socketManager: JupyterBaseSockets,
    protected val commManager: CommManagerInternal,
    protected val executor: JupyterExecutor,
    protected val executionCount: AtomicLong,
    protected val repl: ReplForJupyter,
) : AbstractMessageRequestProcessor(rawIncomingMessage),
    JupyterCommunicationFacility {
    final override val messageFactory = run {
        messageFactoryProvider.update(rawIncomingMessage)
        messageFactoryProvider.provide()!!
    }

    protected val stdinIn: InputStream = StdinInputStream(socketManager.stdin, messageFactory)

    override fun processUnknownShellMessage(content: MessageContent) {
        socketManager.shell.sendMessage(
            messageFactory.makeReplyMessage(MessageType.NONE),
        )
    }

    override fun processUnknownControlMessage(content: MessageContent) {
    }

    override fun processIsCompleteRequest(content: IsCompleteRequest) {
        socketManager.shell.sendMessage(
            messageFactory.makeReplyMessage(MessageType.IS_COMPLETE_REPLY, content = IsCompleteReply("complete")),
        )
    }

    override fun processListErrorsRequest(content: ListErrorsRequest) {
        executor.launchJob {
            repl.listErrors(content.code) { result ->
                sendWrapped(messageFactory.makeReplyMessage(MessageType.LIST_ERRORS_REPLY, content = result.message))
            }
        }
    }

    override fun processCompleteRequest(content: CompleteRequest) {
        executor.launchJob {
            repl.complete(content.code, content.cursorPos) { result ->
                sendWrapped(messageFactory.makeReplyMessage(MessageType.COMPLETE_REPLY, content = result.message))
            }
        }
    }

    override fun processCommMsg(content: CommMsg) {
        executor.runExecution("Execution of comm_msg request for ${content.commId}") {
            commManager.processCommMessage(incomingMessage, content)
        }
    }

    override fun processCommClose(content: CommClose) {
        executor.runExecution("Execution of comm_close request for ${content.commId}") {
            commManager.processCommClose(incomingMessage, content)
        }
    }

    override fun processCommOpen(content: CommOpen) {
        executor.runExecution("Execution of comm_open request for ${content.commId} of target ${content.targetName}") {
            commManager.processCommOpen(incomingMessage, content)
                ?: throw ReplException("Cannot open comm for ${content.commId} of target ${content.targetName}")
        }
    }

    override fun processCommInfoRequest(content: CommInfoRequest) {
        val comms = commManager.getComms(content.targetName)
        val replyMap = comms.associate { comm -> comm.id to Comm(comm.target) }
        sendWrapped(messageFactory.makeReplyMessage(MessageType.COMM_INFO_REPLY, content = CommInfoReply(replyMap)))
    }

    override fun processExecuteRequest(content: ExecuteRequest) {
        val count = executionCount.getAndUpdate {
            if (content.storeHistory) it + 1 else it
        }
        val startedTime = ISO8601DateNow

        doWrappedInBusyIdle {
            val code = content.code
            socketManager.iopub.sendMessage(
                messageFactory.makeReplyMessage(
                    MessageType.EXECUTE_INPUT,
                    content = ExecutionInputReply(code, count),
                ),
            )
            val response: Response = if (looksLikeReplCommand(code)) {
                runCommand(code, repl)
            } else {
                evalWithIO(content.allowStdin) {
                    runExecution("Execution of code '${code.presentableForThreadName()}'") {
                        repl.evalEx(
                            EvalRequestData(
                                code,
                                count.toInt(),
                                content.storeHistory,
                                content.silent,
                            ),
                        )
                    }
                }
            }

            sendResponse(response, count, startedTime)
        }
    }

    override fun processConnectRequest(content: ConnectRequest) {
        sendWrapped(
            messageFactory.makeReplyMessage(
                MessageType.CONNECT_REPLY,
                content = ConnectReply(
                    Json.EMPTY,
                ),
            ),
        )
    }

    override fun processHistoryRequest(content: HistoryRequest) {
        sendWrapped(
            messageFactory.makeReplyMessage(
                MessageType.HISTORY_REPLY,
                content = HistoryReply(listOf()), // not implemented
            ),
        )
    }

    override fun processKernelInfoRequest(content: KernelInfoRequest) {
        sendWrapped(
            messageFactory.makeReplyMessage(
                MessageType.KERNEL_INFO_REPLY,
                content = KernelInfoReply(
                    protocolVersion,
                    "Kotlin",
                    currentKernelVersion.toMaybeUnspecifiedString(),
                    "Kotlin kernel v. ${currentKernelVersion.toMaybeUnspecifiedString()}, Kotlin v. $currentKotlinVersion",
                    notebookLanguageInfo,
                    listOf(),
                ),
            ),
        )
    }

    override fun processShutdownRequest(content: ShutdownRequest) {
        repl.evalOnShutdown()
        socketManager.control.sendMessage(
            messageFactory.makeReplyMessage(MessageType.SHUTDOWN_REPLY, content = incomingMessage.content),
        )
        // exitProcess would kill the entire process that embedded the kernel
        // Instead the controlThread will be interrupted,
        // which will then interrupt the mainThread and make kernelServer return
        if (repl.isEmbedded) {
            LOG.info("Interrupting controlThread to trigger kernel shutdown")
            throw InterruptedException()
        } else {
            exitProcess(0)
        }
    }

    override fun processInterruptRequest(content: InterruptRequest) {
        executor.interruptExecutions()
        socketManager.control.sendMessage(
            messageFactory.makeReplyMessage(MessageType.INTERRUPT_REPLY, content = incomingMessage.content),
        )
    }

    protected open fun runExecution(
        executionName: String,
        execution: () -> EvalResultEx,
    ): Response {
        return when (
            val res = executor.runExecution(
                executionName,
                repl.currentClassLoader,
                execution,
            )
        ) {
            is ExecutionResult.Success -> {
                try {
                    rawToResponse(res.result.displayValue, res.result.metadata)
                } catch (e: Exception) {
                    AbortResponseWithMessage("error:  Unable to convert result to a string: $e")
                }
            }
            is ExecutionResult.Failure -> {
                val ex = res.throwable
                if (ex !is ReplException) throw ex
                (ex as? ReplEvalRuntimeException)?.cause?.let { originalThrowable ->
                    repl.throwableRenderersProcessor.renderThrowable(originalThrowable)
                }?.let { renderedThrowable ->
                    rawToResponse(renderedThrowable.toDisplayResult(repl.notebook))
                } ?: ErrorResponseWithMessage(
                    ex.render(),
                    ex.javaClass.canonicalName,
                    ex.message ?: "",
                    ex.stackTrace.map { it.toString() },
                    ex.getAdditionalInfoJson() ?: Json.EMPTY,
                )
            }
            ExecutionResult.Interrupted -> {
                AbortResponseWithMessage(EXECUTION_INTERRUPTED_MESSAGE)
            }
        }
    }

    protected open fun <T> evalWithIO(
        allowStdIn: Boolean,
        body: () -> T,
    ): T {
        val config = repl.options.outputConfig
        val out = System.out
        val err = System.err
        repl.notebook.beginEvalSession()
        val cell = { repl.notebook.currentCell }

        fun getCapturingStream(stream: PrintStream?, outType: JupyterOutType, captureOutput: Boolean): CapturingOutputStream {
            return CapturingOutputStream(
                stream,
                config,
                captureOutput,
            ) { text ->
                cell()?.appendStreamOutput(text)
                this.sendOut(outType, text)
            }
        }

        val forkedOut = getCapturingStream(out, JupyterOutType.STDOUT, config.captureOutput)
        val forkedError = getCapturingStream(err, JupyterOutType.STDERR, false)
        val userError = getCapturingStream(null, JupyterOutType.STDERR, true)

        fun flushStreams() {
            forkedOut.flush()
            forkedError.flush()
            userError.flush()
        }

        val printForkedOut = PrintStream(forkedOut, false, "UTF-8")
        val printForkedErr = PrintStream(forkedError, false, "UTF-8")
        val printUserError = PrintStream(userError, false, "UTF-8")

        KernelStreams.setStreams(true, printForkedOut, printUserError)

        System.setOut(printForkedOut)
        System.setErr(printForkedErr)

        val `in` = System.`in`
        System.setIn(if (allowStdIn) stdinIn else DisabledStdinInputStream)
        try {
            return body()
        } finally {
            flushStreams()
            System.setIn(`in`)
            System.setErr(err)
            System.setOut(out)

            KernelStreams.setStreams(false, out, err)
        }
    }

    private fun rawToResponse(value: DisplayResult?, metadata: EvaluatedSnippetMetadata = EvaluatedSnippetMetadata.EMPTY): Response {
        return OkResponseWithMessage(value, metadata)
    }

    private fun Code.presentableForThreadName(): String {
        val newName = substringBefore('\n').take(20)
        return if (newName.length < length) "$newName..."
        else this
    }

    companion object {
        private val LOG = logger<MessageRequestProcessor>()
    }
}
