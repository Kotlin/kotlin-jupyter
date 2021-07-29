package org.jetbrains.kotlinx.jupyter

import ch.qos.logback.classic.Level
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlinx.jupyter.LoggingManagement.disableLogging
import org.jetbrains.kotlinx.jupyter.LoggingManagement.mainLoggerLevel
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion.Companion.toMaybeUnspecifiedString
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.Renderable
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.setDisplayId
import org.jetbrains.kotlinx.jupyter.api.textResult
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.config.KernelStreams
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
import org.jetbrains.kotlinx.jupyter.config.currentKotlinVersion
import org.jetbrains.kotlinx.jupyter.config.notebookLanguageInfo
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.repl.EvalResult
import org.jetbrains.kotlinx.jupyter.repl.rawToResponse
import org.jetbrains.kotlinx.jupyter.repl.toResponse
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

abstract class Response(
    private val stdOut: String?,
    private val stdErr: String?
) {
    abstract val state: ResponseState

    fun send(socket: JupyterConnection.Socket, requestCount: Long, requestMsg: Message, startedTime: String) {
        if (stdOut != null && stdOut.isNotEmpty()) {
            socket.connection.iopub.sendOut(requestMsg, JupyterOutType.STDOUT, stdOut)
        }
        if (stdErr != null && stdErr.isNotEmpty()) {
            socket.connection.iopub.sendOut(requestMsg, JupyterOutType.STDERR, stdErr)
        }
        sendBody(socket, requestCount, requestMsg, startedTime)
    }

    protected abstract fun sendBody(socket: JupyterConnection.Socket, requestCount: Long, requestMsg: Message, startedTime: String)
}

class OkResponseWithMessage(
    private val result: DisplayResult?,
    private val metadata: EvaluatedSnippetMetadata? = null,
) : Response(null, null) {
    override val state: ResponseState = ResponseState.Ok

    override fun sendBody(socket: JupyterConnection.Socket, requestCount: Long, requestMsg: Message, startedTime: String) {
        if (result != null) {
            val resultJson = result.toJson()

            socket.connection.iopub.send(
                makeReplyMessage(
                    requestMsg,
                    MessageType.EXECUTE_RESULT,
                    content = ExecutionResult(
                        executionCount = requestCount,
                        data = resultJson["data"]!!,
                        metadata = resultJson["metadata"]!!
                    )
                )
            )
        }

        socket.send(
            makeReplyMessage(
                requestMsg,
                MessageType.EXECUTE_REPLY,
                metadata = jsonObject(
                    "dependencies_met" to Json.encodeToJsonElement(true),
                    "engine" to Json.encodeToJsonElement(requestMsg.data.header?.session),
                    "status" to Json.encodeToJsonElement("ok"),
                    "started" to Json.encodeToJsonElement(startedTime),
                    "eval_metadata" to Json.encodeToJsonElement(metadata),
                ),
                content = ExecuteReply(
                    MessageStatus.OK,
                    requestCount,
                )
            )
        )
    }
}

interface DisplayHandler {
    fun handleDisplay(value: Any, host: ExecutionHost)
    fun handleUpdate(value: Any, host: ExecutionHost, id: String? = null)
}

class SocketDisplayHandler(
    private val socket: JupyterConnection.Socket,
    private val notebook: NotebookImpl,
    private val message: Message,
) : DisplayHandler {
    private fun render(host: ExecutionHost, value: Any): DisplayResult? {
        val renderedValue = notebook.renderersProcessor.renderValue(host, value)
        return renderedValue.toDisplayResult(notebook)
    }

    override fun handleDisplay(value: Any, host: ExecutionHost) {
        val display = render(host, value) ?: return
        val json = display.toJson()

        notebook.currentCell?.addDisplay(display)

        socket.send(
            makeReplyMessage(
                message,
                MessageType.DISPLAY_DATA,
                content = DisplayDataResponse(
                    json["data"],
                    json["metadata"],
                    json["transient"]
                )
            )
        )
    }

    override fun handleUpdate(value: Any, host: ExecutionHost, id: String?) {
        val display = render(host, value) ?: return
        val json = display.toJson().toMutableMap()

        notebook.currentCell?.displays?.update(id, display)

        json.setDisplayId(id) ?: throw RuntimeException("`update_display_data` response should provide an id of data being updated")

        socket.send(
            makeReplyMessage(
                message,
                MessageType.UPDATE_DISPLAY_DATA,
                content = DisplayDataResponse(
                    json["data"],
                    json["metadata"],
                    json["transient"]
                )
            )
        )
    }
}

class AbortResponseWithMessage(
    stdErr: String? = null,
) : Response(null, stdErr) {
    override val state: ResponseState = ResponseState.Abort

    override fun sendBody(socket: JupyterConnection.Socket, requestCount: Long, requestMsg: Message, startedTime: String) {
        val errorReply = makeReplyMessage(
            requestMsg,
            MessageType.EXECUTE_REPLY,
            content = ExecuteReply(MessageStatus.ABORT, requestCount)
        )
        System.err.println("Sending abort: $errorReply")
        socket.send(errorReply)
    }
}

class ErrorResponseWithMessage(
    stdErr: String? = null,
    private val errorName: String = "Unknown error",
    private var errorValue: String = "",
    private val traceback: List<String> = emptyList(),
    private val additionalInfo: JsonObject = emptyJsonObject,
) : Response(null, stdErr) {
    override val state: ResponseState = ResponseState.Error

    override fun sendBody(socket: JupyterConnection.Socket, requestCount: Long, requestMsg: Message, startedTime: String) {
        val errorReply = makeReplyMessage(
            requestMsg,
            MessageType.EXECUTE_REPLY,
            content = ExecuteErrorReply(requestCount, errorName, errorValue, traceback, additionalInfo)
        )
        System.err.println("Sending error: $errorReply")
        socket.send(errorReply)
    }
}

fun JupyterConnection.Socket.controlMessagesHandler(msg: Message, repl: ReplForJupyter?) {
    when (msg.content) {
        is InterruptRequest -> {
            connection.interruptExecution()
            send(makeReplyMessage(msg, MessageType.INTERRUPT_REPLY, content = msg.content))
        }
        is ShutdownRequest -> {
            repl?.evalOnShutdown()
            send(makeReplyMessage(msg, MessageType.SHUTDOWN_REPLY, content = msg.content))
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

fun JupyterConnection.Socket.shellMessagesHandler(msg: Message, repl: ReplForJupyter, executionCount: AtomicLong) {
    when (val content = msg.content) {
        is KernelInfoRequest ->
            sendWrapped(
                msg,
                makeReplyMessage(
                    msg,
                    MessageType.KERNEL_INFO_REPLY,
                    content = KernelInfoReply(
                        protocolVersion,
                        "Kotlin",
                        currentKernelVersion.toMaybeUnspecifiedString(),
                        "Kotlin kernel v. ${currentKernelVersion.toMaybeUnspecifiedString()}, Kotlin v. $currentKotlinVersion",
                        notebookLanguageInfo,
                        listOf()
                    ),
                )
            )

        is HistoryRequest ->
            sendWrapped(
                msg,
                makeReplyMessage(
                    msg,
                    MessageType.HISTORY_REPLY,
                    content = HistoryReply(listOf()) // not implemented

                )
            )

        // TODO: This request is deprecated since messaging protocol v.5.1,
        // remove it in future versions of kernel
        is ConnectRequest ->
            sendWrapped(
                msg,
                makeReplyMessage(
                    msg,
                    MessageType.CONNECT_REPLY,
                    content = ConnectReply(
                        jsonObject(
                            JupyterSockets.values()
                                .map { Pair("${it.nameForUser}_port", connection.config.ports[it.ordinal]) }
                        )
                    )
                )
            )

        is ExecuteRequest -> {
            connection.contextMessage = msg
            val count = executionCount.getAndUpdate {
                if (content.storeHistory) it + 1 else it
            }
            val startedTime = ISO8601DateNow

            val displayHandler = SocketDisplayHandler(connection.iopub, repl.notebook, msg)

            connection.iopub.sendStatus(KernelStatus.BUSY, msg)

            val code = content.code
            connection.iopub.send(
                makeReplyMessage(
                    msg,
                    MessageType.EXECUTE_INPUT,
                    content = ExecutionInputReply(code, count)
                )
            )
            val res: Response = if (looksLikeReplCommand(code)) {
                runCommand(code, repl)
            } else {
                connection.evalWithIO(repl, msg) {
                    repl.eval(
                        EvalRequestData(
                            code,
                            displayHandler,
                            count.toInt(),
                            content.storeHistory,
                            content.silent,
                        )
                    )
                }
            }

            res.send(this, count, msg, startedTime)

            connection.iopub.sendStatus(KernelStatus.IDLE, msg)
            connection.contextMessage = null
        }
        is CommInfoRequest -> {
            sendWrapped(msg, makeReplyMessage(msg, MessageType.COMM_INFO_REPLY, content = CommInfoReply(mapOf())))
        }
        is CompleteRequest -> {
            connection.launchJob {
                repl.complete(content.code, content.cursorPos) { result ->
                    sendWrapped(msg, makeReplyMessage(msg, MessageType.COMPLETE_REPLY, content = result.message))
                }
            }
        }
        is ListErrorsRequest -> {
            connection.launchJob {
                repl.listErrors(content.code) { result ->
                    sendWrapped(msg, makeReplyMessage(msg, MessageType.LIST_ERRORS_REPLY, content = result.message))
                }
            }
        }
        is SerializationRequest -> {
            GlobalScope.launch(Dispatchers.Default) {
                repl.serializeVariables(content.cellId, content.descriptorsState) { result ->
                    sendWrapped(msg, makeReplyMessage(msg, MessageType.SERIALIZATION_REPLY, content = result))
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
            send(makeReplyMessage(msg, MessageType.IS_COMPLETE_REPLY, content = IsCompleteReply(resStr)))
        }
        else -> send(makeReplyMessage(msg, MessageType.NONE))
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
        }
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

fun Any?.toDisplayResult(notebook: Notebook): DisplayResult? = when (this) {
    null -> textResult("null")
    is DisplayResult -> this
    is Renderable -> this.render(notebook)
    is Unit -> null
    else -> textResult(this.toString())
}

fun JupyterConnection.evalWithIO(repl: ReplForJupyter, srcMessage: Message, body: () -> EvalResult?): Response {
    val config = repl.outputConfig
    val out = System.out
    val err = System.err
    repl.notebook.beginEvalSession()
    val cell = { repl.notebook.currentCell }

    fun getCapturingStream(stream: PrintStream?, outType: JupyterOutType, captureOutput: Boolean): CapturingOutputStream {
        return CapturingOutputStream(
            stream,
            config,
            captureOutput
        ) { text ->
            cell()?.appendStreamOutput(text)
            this.iopub.sendOut(srcMessage, outType, text)
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
    val allowStdIn = (srcMessage.content as? ExecuteRequest)?.allowStdin ?: true
    System.setIn(if (allowStdIn) stdinIn else DisabledStdinInputStream)
    try {
        return try {
            val (exec, execException, executionInterrupted) = runExecution(body)
            when {
                executionInterrupted -> {
                    flushStreams()
                    AbortResponseWithMessage("The execution was interrupted")
                }
                execException != null -> {
                    throw execException
                }
                exec == null -> {
                    AbortResponseWithMessage("NO REPL!")
                }
                else -> {
                    flushStreams()
                    try {
                        exec.toResponse(repl.notebook)
                    } catch (e: Exception) {
                        AbortResponseWithMessage("error:  Unable to convert result to a string: $e")
                    }
                }
            }
        } catch (ex: ReplException) {
            flushStreams()

            (ex as? ReplEvalRuntimeException)?.cause?.let { originalThrowable ->
                repl.throwableRenderersProcessor.renderThrowable(originalThrowable)
            }?.let { renderedThrowable ->
                rawToResponse(renderedThrowable, repl.notebook)
            } ?: ErrorResponseWithMessage(
                ex.render(),
                ex.javaClass.canonicalName,
                ex.message ?: "",
                ex.stackTrace.map { it.toString() },
                ex.getAdditionalInfoJson() ?: emptyJsonObject
            )
        }
    } finally {
        flushStreams()
        System.setIn(`in`)
        System.setErr(err)
        System.setOut(out)

        KernelStreams.setStreams(false, out, err)
    }
}
