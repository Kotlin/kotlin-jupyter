package org.jetbrains.kotlinx.jupyter

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion.Companion.toMaybeUnspecifiedString
import org.jetbrains.kotlinx.jupyter.api.MutableJsonObject
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.Renderable
import org.jetbrains.kotlinx.jupyter.api.setDisplayId
import org.jetbrains.kotlinx.jupyter.api.textResult
import org.jetbrains.kotlinx.jupyter.compiler.util.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.compiler.util.ReplException
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScriptsData
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.timer
import kotlin.system.exitProcess

enum class ResponseState {
    Ok, Error, Abort
}

enum class JupyterOutType {
    STDOUT, STDERR;
    fun optionName() = name.toLowerCase()
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
    private val newClasspath: Classpath = emptyList(),
    private val compiledData: SerializedCompiledScriptsData? = null,
    stdOut: String? = null,
    stdErr: String? = null,
) : Response(stdOut, stdErr) {
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
                    "compiled_data" to Json.encodeToJsonElement(compiledData),
                    "new_classpath" to Json.encodeToJsonElement(newClasspath),
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
    fun handleDisplay(value: Any)
    fun handleUpdate(value: Any, id: String? = null)
}

class SocketDisplayHandler(
    private val socket: JupyterConnection.Socket,
    private val notebook: NotebookImpl,
    private val message: Message,
) : DisplayHandler {
    override fun handleDisplay(value: Any) {
        val display = value.toDisplayResult(notebook) ?: return
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

    override fun handleUpdate(value: Any, id: String?) {
        val display = value.toDisplayResult(notebook) ?: return
        val json: MutableJsonObject = display.toJson().toMutableMap()

        notebook.currentCell?.displays?.update(id, display)

        json.setDisplayId(id) ?: throw ReplEvalRuntimeException("`update_display_data` response should provide an id of data being updated")

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
            log.warn("Interruption is not yet supported!")
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
                        repl.runtimeProperties.version.toMaybeUnspecifiedString(),
                        "Kotlin kernel v. ${repl.runtimeProperties.version.toMaybeUnspecifiedString()}, Kotlin v. ${KotlinCompilerVersion.VERSION}",
                        LanguageInfo(
                            "kotlin",
                            KotlinCompilerVersion.VERSION,
                            "text/x-kotlin",
                            ".kt",
                            "kotlin",
                            "text/x-kotlin",
                            ""
                        ),
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
            val count = executionCount.getAndIncrement()
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
            val res: Response = if (isCommand(code)) {
                runCommand(code, repl)
            } else {
                connection.evalWithIO(repl, msg) {
                    repl.eval(code, displayHandler, count.toInt())
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
            GlobalScope.launch {
                repl.complete(content.code, content.cursorPos) { result ->
                    sendWrapped(msg, makeReplyMessage(msg, MessageType.COMPLETE_REPLY, content = result.message))
                }
            }
        }
        is ListErrorsRequest -> {
            GlobalScope.launch {
                repl.listErrors(content.code) { result ->
                    sendWrapped(msg, makeReplyMessage(msg, MessageType.LIST_ERRORS_REPLY, content = result.message))
                }
            }
        }
        is IsCompleteRequest -> {
            val resStr = if (isCommand(content.code)) "complete" else {
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
            sendWrapped(msg, makeReplyMessage(msg, MessageType.IS_COMPLETE_REPLY, content = IsCompleteReply(resStr)))
        }
        else -> send(makeReplyMessage(msg, MessageType.NONE))
    }
}

class CapturingOutputStream(
    private val stdout: PrintStream,
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
        stdout.write(b)

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

fun Any.toDisplayResult(notebook: Notebook): DisplayResult? = when (this) {
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

    fun getCapturingStream(stream: PrintStream, outType: JupyterOutType, captureOutput: Boolean): CapturingOutputStream {
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

    System.setOut(PrintStream(forkedOut, false, "UTF-8"))
    System.setErr(PrintStream(forkedError, false, "UTF-8"))

    val `in` = System.`in`
    val allowStdIn = (srcMessage.content as? ExecuteRequest)?.allowStdin ?: true
    System.setIn(if (allowStdIn) stdinIn else DisabledStdinInputStream)
    try {
        return try {
            val exec = body()
            if (exec == null) {
                AbortResponseWithMessage("NO REPL!")
            } else {
                forkedOut.flush()
                forkedError.flush()

                try {
                    val result = exec.resultValue?.toDisplayResult(repl.notebook)
                    OkResponseWithMessage(result, exec.newClasspath, exec.compiledData)
                } catch (e: Exception) {
                    AbortResponseWithMessage("error:  Unable to convert result to a string: $e")
                }
            }
        } catch (ex: ReplCompilerException) {
            forkedOut.flush()
            forkedError.flush()

            val firstDiagnostic = ex.firstDiagnostics
            val additionalInfo = firstDiagnostic?.location?.let {
                val errorMessage = firstDiagnostic.message
                jsonObject(
                    "lineStart" to Json.encodeToJsonElement(it.start.line),
                    "colStart" to Json.encodeToJsonElement(it.start.col),
                    "lineEnd" to Json.encodeToJsonElement(it.end?.line ?: -1),
                    "colEnd" to Json.encodeToJsonElement(it.end?.col ?: -1),
                    "message" to Json.encodeToJsonElement(errorMessage),
                    "path" to Json.encodeToJsonElement(firstDiagnostic.sourcePath.orEmpty())
                )
            } ?: emptyJsonObject

            ErrorResponseWithMessage(
                ex.message,
                ex.javaClass.canonicalName,
                ex.message ?: "",
                ex.stackTrace.map { it.toString() },
                additionalInfo
            )
        } catch (ex: ReplException) {
            forkedOut.flush()

            val stdErr = StringBuilder()
            with(stdErr) {
                val cause = ex.cause
                if (cause == null) appendLine(ex.message)
                else {
                    when (cause) {
                        is InvocationTargetException -> appendLine(cause.targetException.toString())
                        else -> appendLine(cause.toString())
                    }
                    cause.stackTrace?.also {
                        for (s in it)
                            appendLine(s)
                    }
                }
            }
            ErrorResponseWithMessage(
                stdErr.toString(),
                ex.javaClass.canonicalName,
                ex.message ?: "",
                ex.stackTrace.map { it.toString() }
            )
        }
    } finally {
        forkedOut.close()
        forkedError.close()
        System.setIn(`in`)
        System.setErr(err)
        System.setOut(out)
    }
}
