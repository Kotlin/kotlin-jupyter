package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion.Companion.toMaybeUnspecifiedString
import org.jetbrains.kotlinx.jupyter.api.StreamSubstitutionType
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.commands.runCommand
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
import org.jetbrains.kotlinx.jupyter.config.currentKotlinVersion
import org.jetbrains.kotlinx.jupyter.config.notebookLanguageInfo
import org.jetbrains.kotlinx.jupyter.execution.ExecutionResult
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.protocol.JUPYTER_PROTOCOL_VERSION
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerSockets
import org.jetbrains.kotlinx.jupyter.protocol.api.EMPTY
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import org.jetbrains.kotlinx.jupyter.repl.EvalRequestData
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.repl.result.EvalResultEx
import org.jetbrains.kotlinx.jupyter.streams.CapturingOutputStream
import org.jetbrains.kotlinx.jupyter.streams.DisabledStdinInputStream
import org.jetbrains.kotlinx.jupyter.streams.StdinInputStream
import org.jetbrains.kotlinx.jupyter.streams.StreamSubstitutionManager
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.InvalidPathException
import kotlin.reflect.KProperty
import kotlin.system.exitProcess

private object StdIOSubstitutionManager {
    // We assume that inside one environment there is only one correct value for this property
    var substitutionEngineType: StreamSubstitutionType by InitOnce()

    var useThreadLocal: Boolean by InitOnce()

    val stdoutContext by lazy {
        StreamSubstitutionManager.StdOut(substitutionEngineType, threadLocalStreamSubstitution = useThreadLocal)
    }

    val stdinContext by lazy {
        StreamSubstitutionManager.StdIn(substitutionEngineType, threadLocalStreamSubstitution = useThreadLocal)
    }

    val stderrContext by lazy {
        StreamSubstitutionManager.StdErr(substitutionEngineType, threadLocalStreamSubstitution = useThreadLocal)
    }
}

@Suppress("MemberVisibilityCanBePrivate")
open class IdeCompatibleMessageRequestProcessor(
    rawIncomingMessage: RawMessage,
    messageFactoryProvider: MessageFactoryProvider,
    final override val socketManager: JupyterServerSockets,
    protected val commManager: CommManagerInternal,
    protected val executor: JupyterExecutor,
    protected val executionCounter: ExecutionCounter,
    loggerFactory: KernelLoggerFactory,
    protected val repl: ReplForJupyter,
) : AbstractMessageRequestProcessor(rawIncomingMessage),
    JupyterCommunicationFacility {
    private val logger = loggerFactory.getLogger(this::class)

    init {
        StdIOSubstitutionManager.substitutionEngineType = repl.notebook.kernelRunMode.streamSubstitutionType
        StdIOSubstitutionManager.useThreadLocal = repl.notebook.kernelRunMode.threadLocalStreamSubstitution
    }

    final override val messageFactory =
        run {
            messageFactoryProvider.update(rawIncomingMessage)
            messageFactoryProvider.provide()!!
        }

    @Suppress("LeakingThis")
    protected val stdinIn: InputStream = StdinInputStream(this)

    override fun processUnknownShellMessage(content: MessageContent) {
        socketManager.shell.sendMessage(
            messageFactory.makeReplyMessage(MessageType.NONE),
        )
    }

    override fun processUnknownControlMessage(content: MessageContent) {
    }

    override fun processUnknownStdinMessage(content: MessageContent) {
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

    override fun processCommMsg(content: CommMsgMessage) {
        executor.runExecution("Execution of comm_msg request for ${content.commId}") {
            commManager.processCommMessage(incomingMessage, content)
        }
    }

    override fun processCommClose(content: CommCloseMessage) {
        executor.runExecution("Execution of comm_close request for ${content.commId}") {
            commManager.processCommClose(incomingMessage, content)
        }
    }

    override fun processCommOpen(content: CommOpenMessage) {
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
        val count = executionCounter.next(content.storeHistory)
        val startedTime = ISO8601DateNow

        doWrappedInBusyIdle {
            val code = content.code
            socketManager.iopub.sendMessage(
                messageFactory.makeReplyMessage(
                    MessageType.EXECUTE_INPUT,
                    content = ExecuteInput(code, count),
                ),
            )
            val response: JupyterResponse =
                if (looksLikeReplCommand(code)) {
                    runCommand(code, repl)
                } else {
                    runExecution("Execution of code '${code.presentableForThreadName()}'") {
                        evalWithIO(content.allowStdin) {
                            repl.evalEx(
                                EvalRequestData(
                                    code,
                                    count,
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
                content =
                    ConnectReply(
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
                content =
                    KernelInfoReply(
                        JUPYTER_PROTOCOL_VERSION,
                        "Kotlin",
                        currentKernelVersion.toMaybeUnspecifiedString(),
                        "Kotlin kernel v. ${currentKernelVersion.toMaybeUnspecifiedString()}, Kotlin v. $currentKotlinVersion",
                        notebookLanguageInfo,
                        listOf(),
                    ),
                metadata =
                    Json.encodeToJsonElement(
                        KernelInfoReplyMetadata(repl.currentSessionState),
                    ),
            ),
        )
    }

    override fun processUpdateClientMetadata(content: UpdateClientMetadataRequest) {
        val replyContent =
            try {
                val path = content.absoluteNotebookFilePath
                repl.notebook.updateFilePath(path)
                UpdateClientMetadataSuccessReply()
            } catch (ex: InvalidPathException) {
                logger.error("Invalid notebook file path: ${content.absoluteNotebookFilePath}", ex)
                UpdateClientMetadataErrorReply(ex)
            }
        sendWrapped(
            messageFactory.makeReplyMessage(
                MessageType.UPDATE_CLIENT_METADATA_REPLY,
                content = replyContent,
            ),
        )
    }

    override fun processShutdownRequest(content: ShutdownRequest) {
        repl.evalOnShutdown()
        executor.close()
        socketManager.control.sendMessage(
            messageFactory.makeReplyMessage(MessageType.SHUTDOWN_REPLY, content = incomingMessage.content),
        )
        if (repl.kernelRunMode.shouldKillProcessOnShutdown) {
            exitProcess(0)
        } else {
            // exitProcess would kill the entire process that embedded the kernel
            // Instead the controlThread will be interrupted,
            // which will then interrupt the mainThread and make kernelServer return
            logger.info("Interrupting controlThread to trigger kernel shutdown")
            throw InterruptedException()
        }
    }

    override fun processInterruptRequest(content: InterruptRequest) {
        executor.interruptExecution()
        socketManager.control.sendMessage(
            messageFactory.makeReplyMessage(MessageType.INTERRUPT_REPLY, content = incomingMessage.content),
        )
    }

    override fun processInputReply(content: InputReply) {
    }

    protected open fun runExecution(
        executionName: String,
        execution: () -> EvalResultEx,
    ): JupyterResponse =
        when (
            val res =
                executor.runExecution(
                    executionName,
                    repl.currentClassLoader,
                    execution,
                )
        ) {
            is ExecutionResult.Success -> {
                try {
                    when (val replResult = res.result) {
                        is EvalResultEx.Success -> {
                            OkJupyterResponse(replResult.displayValue, replResult.metadata)
                        }
                        is EvalResultEx.Error -> {
                            replResult.error.toErrorJupyterResponse(replResult.metadata)
                        }
                        is EvalResultEx.RenderedError -> {
                            OkJupyterResponse(replResult.displayError, replResult.metadata)
                        }
                        is EvalResultEx.Interrupted -> {
                            ErrorJupyterResponse(EXECUTION_INTERRUPTED_MESSAGE, metadata = replResult.metadata)
                        }
                    }
                } catch (e: Throwable) {
                    ErrorJupyterResponse("error:  Unable to convert result to a string: $e")
                }
            }
            is ExecutionResult.Failure -> {
                res.throwable.toErrorJupyterResponse()
            }
            ExecutionResult.Interrupted -> {
                ErrorJupyterResponse(EXECUTION_INTERRUPTED_MESSAGE)
            }
        }

    private val replOutputConfig get() = repl.options.outputConfig

    /**
     * Creates a capturing [PrintStream] that forwards its output to the given [parentStream] (if not null)
     * and optionally captures the output for further processing.
     * Captured output is sent as a [MessageType.STREAM] message back to the client.
     *
     * @param parentStream the parent [PrintStream] to forward the output to, can be null
     * @param outType the type of output (stdout or stderr) to be associated with the captured output
     * @param captureOutput a flag indicating whether to capture the output
     * @return a new [PrintStream] that wraps the forwards output to the [parentStream]
     * and captures output if [captureOutput] is true
     */
    private fun getCapturingStream(
        parentStream: PrintStream?,
        outType: JupyterOutType,
        captureOutput: Boolean,
    ): PrintStream =
        CapturingOutputStream(
            parentStream,
            replOutputConfig,
            captureOutput,
        ) { text ->
            repl.notebook.currentCell?.appendStreamOutput(text)
            this.sendOut(outType, text)
        }.asPrintStream()

    private fun OutputStream.asPrintStream() = PrintStream(this, false, "UTF-8")

    private fun <T> withForkedOut(body: () -> T): T =
        StdIOSubstitutionManager.stdoutContext.withSubstitutedStreams(
            systemStreamFactory = { out: PrintStream? -> getCapturingStream(out, JupyterOutType.STDOUT, replOutputConfig.captureOutput) },
            kernelStreamFactory = { null },
            body = body,
        )

    private fun <T> withForkedErr(body: () -> T): T =
        StdIOSubstitutionManager.stderrContext.withSubstitutedStreams(
            systemStreamFactory = { err: PrintStream? -> getCapturingStream(err, JupyterOutType.STDERR, false) },
            kernelStreamFactory = { getCapturingStream(null, JupyterOutType.STDERR, true) },
            body = body,
        )

    private fun <T> withForkedIn(
        allowStdIn: Boolean,
        body: () -> T,
    ): T =
        StdIOSubstitutionManager.stdinContext.withSubstitutedStreams(
            systemStreamFactory = { if (allowStdIn) stdinIn else DisabledStdinInputStream },
            kernelStreamFactory = { null },
            body = body,
        )

    protected open fun <T> evalWithIO(
        allowStdIn: Boolean,
        body: () -> T,
    ): T {
        repl.notebook.beginEvalSession()
        return withForkedOut {
            withForkedErr {
                withForkedIn(allowStdIn, body)
            }
        }
    }

    private fun Code.presentableForThreadName(): String {
        val newName = substringBefore('\n').take(20)
        return if (newName.length < length) {
            "$newName..."
        } else {
            this
        }
    }
}

private class InitOnce<T : Any> {
    private var value: T? = null

    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): T = value ?: throw UninitializedPropertyAccessException("${property.name} is not initialized yet")

    operator fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: T,
    ) {
        if (this.value == null) {
            this.value = value
        } else {
            require(this.value == value) {
                "Attempt to set ${property.name} to $value which is different from already set value"
            }
        }
    }
}
