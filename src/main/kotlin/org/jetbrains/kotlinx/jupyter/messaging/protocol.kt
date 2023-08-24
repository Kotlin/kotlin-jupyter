package org.jetbrains.kotlinx.jupyter.messaging

import ch.qos.logback.classic.Level
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlinx.jupyter.DisabledStdinInputStream
import org.jetbrains.kotlinx.jupyter.EvalRequestData
import org.jetbrains.kotlinx.jupyter.ExecutionResult
import org.jetbrains.kotlinx.jupyter.LoggingManagement.disableLogging
import org.jetbrains.kotlinx.jupyter.LoggingManagement.mainLoggerLevel
import org.jetbrains.kotlinx.jupyter.MutableNotebook
import org.jetbrains.kotlinx.jupyter.OutputConfig
import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion.Companion.toMaybeUnspecifiedString
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.api.libraries.portField
import org.jetbrains.kotlinx.jupyter.api.setDisplayId
import org.jetbrains.kotlinx.jupyter.api.withId
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.config.KernelStreams
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
import org.jetbrains.kotlinx.jupyter.config.currentKotlinVersion
import org.jetbrains.kotlinx.jupyter.config.notebookLanguageInfo
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.log
import org.jetbrains.kotlinx.jupyter.presentableForThreadName
import org.jetbrains.kotlinx.jupyter.protocolVersion
import org.jetbrains.kotlinx.jupyter.repl.EvalResultEx
import org.jetbrains.kotlinx.jupyter.repl.rawToResponse
import org.jetbrains.kotlinx.jupyter.repl.renderValue
import org.jetbrains.kotlinx.jupyter.repl.toDisplayResult
import org.jetbrains.kotlinx.jupyter.runCommand
import org.jetbrains.kotlinx.jupyter.util.EMPTY
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.timer
import kotlin.system.exitProcess

enum class ResponseState {
    Ok, Error, Abort
}

enum class JupyterOutType {
    STDOUT, STDERR;
    fun optionName() = name.lowercase()
}

const val EXECUTION_INTERRUPTED_MESSAGE = "The execution was interrupted"

abstract class Response(
    private val stdOut: String?,
    private val stdErr: String?,
) {
    abstract val state: ResponseState

    fun send(connection: JupyterConnectionInternal, requestCount: Long, requestMsg: RawMessage, startedTime: String) {
        if (!stdOut.isNullOrEmpty()) {
            connection.sendOut(requestMsg, JupyterOutType.STDOUT, stdOut)
        }
        if (!stdErr.isNullOrEmpty()) {
            connection.sendOut(requestMsg, JupyterOutType.STDERR, stdErr)
        }
        sendBody(connection, requestCount, requestMsg, startedTime)
    }

    protected abstract fun sendBody(connection: JupyterConnectionInternal, requestCount: Long, requestMsg: RawMessage, startedTime: String)
}

class OkResponseWithMessage(
    private val result: DisplayResult?,
    private val metadata: EvaluatedSnippetMetadata? = null,
) : Response(null, null) {
    override val state: ResponseState = ResponseState.Ok

    override fun sendBody(connection: JupyterConnectionInternal, requestCount: Long, requestMsg: RawMessage, startedTime: String) {
        if (result != null) {
            val resultJson = result.toJson(Json.EMPTY, null)

            connection.socketManager.iopub.sendMessage(
                makeReplyMessage(
                    requestMsg,
                    MessageType.EXECUTE_RESULT,
                    content = ExecutionResultMessage(
                        executionCount = requestCount,
                        data = resultJson["data"]!!,
                        metadata = resultJson["metadata"]!!,
                    ),
                ),
            )
        }

        connection.socketManager.shell.sendMessage(
            makeReplyMessage(
                requestMsg,
                MessageType.EXECUTE_REPLY,
                metadata = jsonObject(
                    "dependencies_met" to Json.encodeToJsonElement(true),
                    "engine" to (requestMsg.header["session"] ?: JsonNull),
                    "status" to Json.encodeToJsonElement("ok"),
                    "started" to Json.encodeToJsonElement(startedTime),
                    "eval_metadata" to Json.encodeToJsonElement(metadata),
                ),
                content = ExecuteReply(
                    MessageStatus.OK,
                    requestCount,
                ),
            ),
        )
    }
}

interface DisplayHandler {
    fun handleDisplay(value: Any, host: ExecutionHost, id: String? = null)
    fun handleUpdate(value: Any, host: ExecutionHost, id: String? = null)
}

object NoOpDisplayHandler : DisplayHandler {
    override fun handleDisplay(value: Any, host: ExecutionHost, id: String?) {
    }

    override fun handleUpdate(value: Any, host: ExecutionHost, id: String?) {
    }
}

class SocketDisplayHandler(
    private val connection: JupyterConnectionInternal,
    private val notebook: MutableNotebook,
) : DisplayHandler {
    private val socket = connection.socketManager.iopub

    override fun handleDisplay(value: Any, host: ExecutionHost, id: String?) {
        val display = renderValue(notebook, host, value)?.let { if (id != null) it.withId(id) else it } ?: return
        val json = display.toJson(Json.EMPTY, null)

        notebook.currentCell?.addDisplay(display)

        val content = DisplayDataResponse(
            json["data"],
            json["metadata"],
            json["transient"],
        )
        val message = connection.messageFactory.makeReplyMessage(MessageType.DISPLAY_DATA, content = content)
        socket.sendMessage(message)
    }

    override fun handleUpdate(value: Any, host: ExecutionHost, id: String?) {
        val display = renderValue(notebook, host, value) ?: return
        val json = display.toJson(Json.EMPTY, null).toMutableMap()

        val container = notebook.displays
        container.update(id, display)
        container.getById(id).distinctBy { it.cell.id }.forEach {
            it.cell.displays.update(id, display)
        }

        json.setDisplayId(id)
            ?: throw RuntimeException("`update_display_data` response should provide an id of data being updated")

        val content = DisplayDataResponse(
            json["data"],
            json["metadata"],
            json["transient"],
        )
        val message = connection.messageFactory.makeSimpleMessage(MessageType.UPDATE_DISPLAY_DATA, content)
        socket.sendMessage(message)
    }
}

class AbortResponseWithMessage(
    stdErr: String? = null,
) : Response(null, stdErr) {
    override val state: ResponseState = ResponseState.Abort

    override fun sendBody(connection: JupyterConnectionInternal, requestCount: Long, requestMsg: RawMessage, startedTime: String) {
        val errorReply = makeReplyMessage(
            requestMsg,
            MessageType.EXECUTE_REPLY,
            content = ExecuteReply(MessageStatus.ABORT, requestCount),
        )
        System.err.println("Sending abort: $errorReply")
        connection.socketManager.shell.sendMessage(errorReply)
    }
}

class ErrorResponseWithMessage(
    stdErr: String? = null,
    private val errorName: String = "Unknown error",
    private var errorValue: String = "",
    private val traceback: List<String> = emptyList(),
    private val additionalInfo: JsonObject = Json.EMPTY,
) : Response(null, stdErr) {
    override val state: ResponseState = ResponseState.Error

    override fun sendBody(connection: JupyterConnectionInternal, requestCount: Long, requestMsg: RawMessage, startedTime: String) {
        val errorReply = makeReplyMessage(
            requestMsg,
            MessageType.EXECUTE_REPLY,
            content = ExecuteErrorReply(requestCount, errorName, errorValue, traceback, additionalInfo),
        )
        System.err.println("Sending error: $errorReply")
        connection.socketManager.shell.sendMessage(errorReply)
    }
}

fun JupyterConnectionInternal.controlMessagesHandler(rawIncomingMessage: RawMessage, repl: ReplForJupyter?) {
    val msg = rawIncomingMessage.toMessage()

    when (msg.content) {
        is InterruptRequest -> {
            executor.interruptExecutions()
            socketManager.control.sendMessage(makeReplyMessage(rawIncomingMessage, MessageType.INTERRUPT_REPLY, content = msg.content))
        }
        is ShutdownRequest -> {
            repl?.evalOnShutdown()
            socketManager.control.sendMessage(makeReplyMessage(rawIncomingMessage, MessageType.SHUTDOWN_REPLY, content = msg.content))
            // exitProcess would kill the entire process that embedded the kernel
            // Instead the controlThread will be interrupted,
            // which will then interrupt the mainThread and make kernelServer return
            if (repl?.isEmbedded == true) {
                log.info("Interrupting controlThread to trigger kernel shutdown")
                throw InterruptedException()
            } else {
                exitProcess(0)
            }
        }
    }
}

fun JupyterConnectionInternal.shellMessagesHandler(
    rawIncomingMessage: RawMessage,
    repl: ReplForJupyter,
    commManager: CommManagerInternal,
    executionCount: AtomicLong,
) {
    val incomingMessage = rawIncomingMessage.toMessage()
    fun sendWrapped(message: Message) = doWrappedInBusyIdle(rawIncomingMessage) {
        socketManager.shell.sendMessage(message)
    }

    when (val content = incomingMessage.content) {
        is KernelInfoRequest ->

            sendWrapped(
                makeReplyMessage(
                    rawIncomingMessage,
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

        is HistoryRequest ->
            sendWrapped(
                makeReplyMessage(
                    rawIncomingMessage,
                    MessageType.HISTORY_REPLY,
                    content = HistoryReply(listOf()), // not implemented
                ),
            )

        // TODO: This request is deprecated since messaging protocol v.5.1,
        // remove it in future versions of kernel
        is ConnectRequest ->
            sendWrapped(
                makeReplyMessage(
                    rawIncomingMessage,
                    MessageType.CONNECT_REPLY,
                    content = ConnectReply(
                        jsonObject(
                            config.ports.map { (socket, port) ->
                                socket.portField to port
                            },
                        ),
                    ),
                ),
            )

        is ExecuteRequest -> {
            messageFactory.updateContextMessage(rawIncomingMessage)
            val count = executionCount.getAndUpdate {
                if (content.storeHistory) it + 1 else it
            }
            val startedTime = ISO8601DateNow

            doWrappedInBusyIdle(rawIncomingMessage) {
                val code = content.code
                socketManager.iopub.sendMessage(
                    makeReplyMessage(
                        rawIncomingMessage,
                        MessageType.EXECUTE_INPUT,
                        content = ExecutionInputReply(code, count),
                    ),
                )
                val res: Response = if (looksLikeReplCommand(code)) {
                    runCommand(code, repl)
                } else {
                    val allowStdIn = (incomingMessage.content as? ExecuteRequest)?.allowStdin ?: true
                    evalWithIO(
                        "Execution of code '${code.presentableForThreadName()}'",
                        repl,
                        rawIncomingMessage,
                        allowStdIn,
                    ) {
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

                res.send(this, count, rawIncomingMessage, startedTime)
            }
            messageFactory.updateContextMessage(null)
        }
        is CommInfoRequest -> {
            val comms = commManager.getComms(content.targetName)
            val replyMap = comms.associate { comm -> comm.id to Comm(comm.target) }
            sendWrapped(makeReplyMessage(rawIncomingMessage, MessageType.COMM_INFO_REPLY, content = CommInfoReply(replyMap)))
        }
        is CommOpen -> {
            executor.runExecution("Execution of comm_open request for ${content.commId} of target ${content.targetName}") {
                commManager.processCommOpen(incomingMessage, content)
            }
        }
        is CommClose -> {
            executor.runExecution("Execution of comm_close request for ${content.commId}") {
                commManager.processCommClose(incomingMessage, content)
            }
        }
        is CommMsg -> {
            executor.runExecution("Execution of comm_msg request for ${content.commId}") {
                commManager.processCommMessage(incomingMessage, content)
            }
        }
        is CompleteRequest -> {
            executor.launchJob {
                repl.complete(content.code, content.cursorPos) { result ->
                    sendWrapped(makeReplyMessage(rawIncomingMessage, MessageType.COMPLETE_REPLY, content = result.message))
                }
            }
        }
        is ListErrorsRequest -> {
            executor.launchJob {
                repl.listErrors(content.code) { result ->
                    sendWrapped(makeReplyMessage(rawIncomingMessage, MessageType.LIST_ERRORS_REPLY, content = result.message))
                }
            }
        }
        is IsCompleteRequest -> {
            // We are in console mode, so switch off all the loggers
            if (mainLoggerLevel() != Level.OFF) disableLogging()

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
            socketManager.shell.sendMessage(makeReplyMessage(rawIncomingMessage, MessageType.IS_COMPLETE_REPLY, content = IsCompleteReply(resStr)))
        }
        else -> socketManager.shell.sendMessage(makeReplyMessage(rawIncomingMessage, MessageType.NONE))
    }
}

class CapturingOutputStream(
    private val stdout: PrintStream?,
    private val conf: OutputConfig,
    private val captureOutput: Boolean,
    val onCaptured: (String) -> Unit,
) : OutputStream() {
    private val capturedLines = ByteArrayOutputStream()
    private val capturedNewLine = ByteArrayOutputStream()
    private var overallOutputSize = 0
    private var newlineFound = false

    private val timer = timer(
        initialDelay = conf.captureBufferTimeLimitMs,
        period = conf.captureBufferTimeLimitMs,
        action = {
            flush()
        },
    )

    val contents: ByteArray
        @TestOnly
        get() = capturedLines.toByteArray() + capturedNewLine.toByteArray()

    private fun flushIfNeeded(b: Int) {
        val c = b.toChar()
        if (c == '\n') {
            newlineFound = true
            capturedNewLine.writeTo(capturedLines)
            capturedNewLine.reset()
        }

        val size = capturedLines.size() + capturedNewLine.size()

        if (newlineFound && size >= conf.captureNewlineBufferSize) {
            return flushBuffers(capturedLines)
        }
        if (size >= conf.captureBufferMaxSize) {
            return flush()
        }
    }

    @Synchronized
    override fun write(b: Int) {
        ++overallOutputSize
        stdout?.write(b)

        if (captureOutput && overallOutputSize <= conf.cellOutputMaxSize) {
            capturedNewLine.write(b)
            flushIfNeeded(b)
        }
    }

    @Synchronized
    private fun flushBuffers(vararg buffers: ByteArrayOutputStream) {
        newlineFound = false
        val str = buffers.map { stream ->
            val str = stream.toString("UTF-8")
            stream.reset()
            str
        }.reduce { acc, s -> acc + s }
        if (str.isNotEmpty()) {
            onCaptured(str)
        }
    }

    override fun flush() {
        flushBuffers(capturedLines, capturedNewLine)
    }

    override fun close() {
        super.close()
        timer.cancel()
    }
}

fun JupyterConnectionInternal.evalWithIO(
    executionName: String,
    repl: ReplForJupyter,
    incomingMessage: RawMessage,
    allowStdIn: Boolean,
    body: () -> EvalResultEx?,
): Response {
    val config = repl.outputConfig
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
            this.sendOut(incomingMessage, outType, text)
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
        return when (
            val res = executor.runExecution(
                executionName,
                repl.currentClassLoader,
                body,
            )
        ) {
            is ExecutionResult.Success -> {
                if (res.result == null) {
                    AbortResponseWithMessage("NO REPL!")
                } else {
                    try {
                        rawToResponse(res.result.displayValue, res.result.metadata)
                    } catch (e: Exception) {
                        AbortResponseWithMessage("error:  Unable to convert result to a string: $e")
                    }
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
    } finally {
        flushStreams()
        System.setIn(`in`)
        System.setErr(err)
        System.setOut(out)

        KernelStreams.setStreams(false, out, err)
    }
}
