package org.jetbrains.kotlinx.jupyter.test.protocol

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.paths.shouldBeAFile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeTypeOf
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import jupyter.kotlin.providers.UserHandlesProvider
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlinx.jupyter.api.MimeTypes
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.SessionOptions
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.config.currentKotlinVersion
import org.jetbrains.kotlinx.jupyter.logging.LogbackLoggingManager
import org.jetbrains.kotlinx.jupyter.messaging.CommMsgMessage
import org.jetbrains.kotlinx.jupyter.messaging.CommOpenMessage
import org.jetbrains.kotlinx.jupyter.messaging.DisplayDataMessage
import org.jetbrains.kotlinx.jupyter.messaging.EXECUTION_INTERRUPTED_MESSAGE
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteErrorReply
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteRequest
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteResult
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteSuccessReply
import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCount
import org.jetbrains.kotlinx.jupyter.messaging.InputReply
import org.jetbrains.kotlinx.jupyter.messaging.InputRequest
import org.jetbrains.kotlinx.jupyter.messaging.InterruptRequest
import org.jetbrains.kotlinx.jupyter.messaging.IsCompleteReply
import org.jetbrains.kotlinx.jupyter.messaging.IsCompleteRequest
import org.jetbrains.kotlinx.jupyter.messaging.KernelInfoReply
import org.jetbrains.kotlinx.jupyter.messaging.KernelInfoReplyMetadata
import org.jetbrains.kotlinx.jupyter.messaging.KernelInfoRequest
import org.jetbrains.kotlinx.jupyter.messaging.KernelStatus
import org.jetbrains.kotlinx.jupyter.messaging.Message
import org.jetbrains.kotlinx.jupyter.messaging.MessageStatus
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.OpenDebugPortReply
import org.jetbrains.kotlinx.jupyter.messaging.ProvidedCommMessages
import org.jetbrains.kotlinx.jupyter.messaging.StatusMessage
import org.jetbrains.kotlinx.jupyter.messaging.StreamMessage
import org.jetbrains.kotlinx.jupyter.messaging.UpdateClientMetadataRequest
import org.jetbrains.kotlinx.jupyter.messaging.UpdateClientMetadataSuccessReply
import org.jetbrains.kotlinx.jupyter.protocol.JupyterReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendSocket
import org.jetbrains.kotlinx.jupyter.protocol.MessageFormat
import org.jetbrains.kotlinx.jupyter.protocol.exceptions.tryFinally
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientReceiveSocketManager
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientReceiveSockets
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.protocol.startup.PortsGenerator
import org.jetbrains.kotlinx.jupyter.protocol.startup.create
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.test.NotebookMock
import org.jetbrains.kotlinx.jupyter.test.assertStartsWith
import org.jetbrains.kotlinx.jupyter.test.testLoggerFactory
import org.jetbrains.kotlinx.jupyter.util.jsonObject
import org.jetbrains.kotlinx.jupyter.ws.JupyterWsClientReceiveSocketManager
import org.jetbrains.kotlinx.jupyter.ws.WsKernelPorts
import org.jetbrains.kotlinx.jupyter.zmq.protocol.JupyterZmqClientReceiveSocketManager
import org.jetbrains.kotlinx.jupyter.zmq.protocol.createRandomZmqKernelPorts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.File
import java.net.ConnectException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

fun JsonObject.string(key: String): String = (get(key) as JsonPrimitive).content

@Timeout(100, unit = TimeUnit.SECONDS)
@Execution(ExecutionMode.SAME_THREAD)
abstract class ExecuteTests(
    private val socketManager: JupyterClientReceiveSocketManager,
    generatePorts: () -> KernelPorts,
) : KernelServerTestsBase(runServerInSeparateProcess = true, generatePorts = generatePorts) {
    private var mutableSockets: JupyterClientReceiveSockets? = null
    private val sockets: JupyterClientReceiveSockets get() = mutableSockets!!

    private val shellSocket: JupyterSendReceiveSocket get() = sockets.shell
    private val controlSocket: JupyterSendSocket get() = sockets.control
    private val ioPubSocket: JupyterReceiveSocket get() = sockets.ioPub
    private val stdinSocket: JupyterSendReceiveSocket get() = sockets.stdin

    private val replCompilerMode get() = kernelConfig.ownParams.replCompilerMode

    override fun beforeEach() {
        try {
            val now = TimeSource.Monotonic.markNow()
            while (now.elapsedNow() < CONNECT_RETRY_TIMEOUT_SECONDS.seconds) {
                try {
                    mutableSockets = socketManager.open(kernelConfig.jupyterParams)
                    break
                } catch (_: ConnectException) {
                    Thread.sleep(500)
                }
            }
            if (mutableSockets == null) error("Could not connect to kernel server")
        } catch (e: Throwable) {
            afterEach()
            throw e
        }
    }

    override fun afterEach() {
        mutableSockets?.let {
            mutableSockets = null
            it.close()
        }
    }

    private fun doExecute(
        code: String,
        hasResult: Boolean = true,
        ioPubChecker: (JupyterReceiveSocket) -> Unit = {},
        executeRequestSent: () -> Unit = {},
        executeReplyChecker: (Message) -> Unit = {},
        inputs: List<String> = emptyList(),
        allowStdin: Boolean = true,
        storeHistory: Boolean = true,
    ): Any? {
        try {
            sockets.shell.sendMessage(
                MessageType.EXECUTE_REQUEST,
                content = ExecuteRequest(code, allowStdin = allowStdin, storeHistory = storeHistory),
            )
            executeRequestSent()
            inputs.forEach {
                val request = stdinSocket.receiveMessage()
                request.content.shouldBeTypeOf<InputRequest>()
                stdinSocket.sendMessage(MessageType.INPUT_REPLY, InputReply(it))
            }

            var msg = shellSocket.receiveMessage()
            assertEquals(MessageType.EXECUTE_REPLY, msg.type)
            executeReplyChecker(msg)

            return withReceivingStatusMessages {
                msg = ioPubSocket.receiveMessage()
                assertEquals(MessageType.EXECUTE_INPUT, msg.type)

                ioPubChecker(ioPubSocket)

                if (hasResult) {
                    msg = ioPubSocket.receiveMessage()
                    val content = msg.content as ExecuteResult
                    assertEquals(MessageType.EXECUTE_RESULT, msg.type)
                    content.data
                } else {
                    null
                }
            }
        } catch (e: Throwable) {
            afterEach()
            throw e
        }
    }

    private fun executeWithNoStdin(code: String) {
        doExecute(
            code,
            hasResult = false,
            allowStdin = false,
            ioPubChecker = {
                val msg = it.receiveMessage()
                assertEquals(MessageType.ERROR, msg.type)
                assertStartsWith("Input from stdin is unsupported by the client", (msg.content as ExecuteErrorReply).value)
            },
        )
    }

    private fun doIsComplete(code: String): String {
        try {
            shellSocket.sendMessage(MessageType.IS_COMPLETE_REQUEST, content = IsCompleteRequest(code))

            val responseMsg = shellSocket.receiveMessage()
            assertEquals(MessageType.IS_COMPLETE_REPLY, responseMsg.type)

            val content = responseMsg.content as IsCompleteReply
            return content.status
        } catch (e: Throwable) {
            afterEach()
            throw e
        }
    }

    private fun interruptExecution() {
        controlSocket.sendMessage(MessageType.INTERRUPT_REQUEST, InterruptRequest())
    }

    private fun requestKernelInfo(): Message {
        shellSocket.sendMessage(MessageType.KERNEL_INFO_REQUEST, KernelInfoRequest())
        val responseMsg =
            withReceivingStatusMessages {
                shellSocket.receiveMessage()
            }
        responseMsg.type shouldBe MessageType.KERNEL_INFO_REPLY
        val content = responseMsg.content
        content.shouldBeTypeOf<KernelInfoReply>()
        return responseMsg
    }

    private fun sendClientMetadata(notebookFile: Path) {
        shellSocket.sendMessage(
            MessageType.UPDATE_CLIENT_METADATA_REQUEST,
            UpdateClientMetadataRequest(notebookFile),
        )
        val responseMsg =
            withReceivingStatusMessages {
                shellSocket.receiveMessage()
            }
        responseMsg.type shouldBe MessageType.UPDATE_CLIENT_METADATA_REPLY
        val content = responseMsg.content
        content.shouldBeTypeOf<UpdateClientMetadataSuccessReply>()
    }

    private fun receiveStatusMessage(status: KernelStatus) {
        val msg = ioPubSocket.receiveMessage()
        msg.type shouldBe MessageType.STATUS
        (msg.content as StatusMessage).status shouldBe status
    }

    // Make sure we also drain the ioPubSocket when sending protocol messages
    private fun <T> withReceivingStatusMessages(body: () -> T): T =
        tryFinally(
            action = {
                receiveStatusMessage(KernelStatus.BUSY)
                body()
            },
            finally = { receiveStatusMessage(KernelStatus.IDLE) },
        )

    private inline fun <reified T : Any> JupyterReceiveSocket.receiveMessageOfType(messageType: MessageType): T {
        val msg = receiveMessage()
        assertEquals(messageType, msg.type)
        val content = msg.content
        content.shouldBeTypeOf<T>()
        return content
    }

    private fun JupyterReceiveSocket.receiveStreamResponse(): String = receiveMessageOfType<StreamMessage>(MessageType.STREAM).text

    private fun JupyterReceiveSocket.receiveErrorResponse(): String = receiveMessageOfType<ExecuteErrorReply>(MessageType.ERROR).value

    private fun JupyterReceiveSocket.receiveDisplayDataResponse(): DisplayDataMessage = receiveMessageOfType(MessageType.DISPLAY_DATA)

    private fun JupyterReceiveSocket.receiveUpdateDisplayDataResponse(): DisplayDataMessage =
        receiveMessageOfType(MessageType.UPDATE_DISPLAY_DATA)

    @Test
    fun testExecute() {
        val res = doExecute("2+2") as JsonObject
        assertEquals("4", res.string(MimeTypes.PLAIN_TEXT))
    }

    @Test
    fun testOutput() {
        val code =
            """
            for (i in 1..5) {
                Thread.sleep(200)
                print(i)
            }
            """.trimIndent()

        fun checker(ioPub: JupyterReceiveSocket) {
            for (i in 1..5) {
                val msg = ioPub.receiveMessage()
                assertEquals(MessageType.STREAM, msg.type)
                assertEquals(i.toString(), (msg.content as StreamMessage).text)
            }
        }

        val res = doExecute(code, false, ::checker)
        assertNull(res)
    }

    @Test
    fun testOutputMagic() {
        val code =
            """
            %output --max-buffer=2 --max-time=10000
            for (i in 1..5) {
                print(i)
            }
            """.trimIndent()

        val expected = arrayOf("12", "34", "5")

        fun checker(ioPub: JupyterReceiveSocket) {
            for (el in expected) {
                val msgText = ioPub.receiveStreamResponse()
                assertEquals(el, msgText)
            }
        }

        val res = doExecute(code, false, ::checker)
        assertNull(res)
    }

    @Test
    fun testOutputStrings() {
        val repetitions = 5
        val code =
            """
            for (i in 1..$repetitions) {
                Thread.sleep(200)
                println("text" + i)
            }
            """.trimIndent()

        fun checker(ioPub: JupyterReceiveSocket) {
            val lineSeparator = System.lineSeparator()
            val actualText = (1..repetitions).joinToString("") { ioPub.receiveStreamResponse() }
            val expectedText = (1..repetitions).joinToString("") { i -> "text$i$lineSeparator" }
            actualText shouldBe expectedText
        }

        val res = doExecute(code, false, ::checker)
        assertNull(res)
    }

    @Test
    fun testReadLine() {
        val code =
            """
            val answer1 = readLine()!!.toInt()
            val answer2 = notebook.prompt("Your answer:").toInt()
            answer1 + answer2
            """.trimIndent()
        val res = doExecute(code, inputs = listOf("42", "43"))
        res shouldBe jsonObject(MimeTypes.PLAIN_TEXT to "85")
    }

    @Test
    fun testCompiledData() {
        doExecute(
            """
            SessionOptions.serializeScriptData = true
            """.trimIndent(),
            hasResult = false,
        )

        val code =
            """
            val xyz = 42
            """.trimIndent()
        val res =
            doExecute(
                code,
                hasResult = false,
                executeReplyChecker = { message ->
                    val metadata = message.data.metadata
                    assertTrue(metadata is JsonObject)
                    val snippetMetadata =
                        MessageFormat.decodeFromJsonElement<EvaluatedSnippetMetadata?>(
                            metadata["eval_metadata"] ?: JsonNull,
                        )
                    val compiledData = snippetMetadata?.compiledData
                    assertNotNull(compiledData)

                    val deserializer = CompiledScriptsSerializer()
                    val dir = Files.createTempDirectory("kotlin-jupyter-exec-test")
                    dir.toFile().deleteOnExit()
                    val classesDir = dir.resolve("classes")
                    val sourcesDir = dir.resolve("sources")

                    val names = deserializer.deserializeAndSave(compiledData, classesDir, sourcesDir)
                    val kClassName = names.single()
                    val classLoader = URLClassLoader(arrayOf(classesDir.toUri().toURL()), ClassLoader.getSystemClassLoader())
                    val loadedClass = classLoader.loadClass(kClassName).kotlin

                    assertNotNull(loadedClass.memberProperties.find { it.name == "xyz" })

                    when (replCompilerMode) {
                        K1 -> {
                            @Suppress("UNCHECKED_CAST")
                            val xyzProperty = loadedClass.memberProperties.single { it.name == "xyz" } as KProperty1<Any, Int>
                            val constructor = loadedClass.constructors.single()
                            val scriptTemplateDisplayHelpers =
                                ScriptTemplateWithDisplayHelpers(
                                    object : UserHandlesProvider {
                                        override val notebook: Notebook = NotebookMock
                                        override val sessionOptions: SessionOptions
                                            get() = throw NotImplementedError()
                                    },
                                )
                            val instance = constructor.call(emptyArray<Any?>(), scriptTemplateDisplayHelpers)
                            xyzProperty.get(instance) shouldBe 42
                        }
                        K2 -> {
                            // `$$eval`-method does not have correct Kotlin metadata, so we fall back to pure Java reflection.
                            assertNotNull(loadedClass.java.declaredMethods.firstOrNull { it.name == "\$\$eval" })
                        }
                    }

                    val sourceFile = sourcesDir.resolve("Line_1.kts")
                    sourceFile.shouldBeAFile()
                    sourceFile.readText().trim() shouldBe "val xyz = 42"
                },
            )
        assertNull(res)
    }

    @Test
    fun testLibraryLoadingErrors() {
        doExecute(
            """
            USE {
                import("xyz.ods")
            }
            """.trimIndent(),
            false,
            ioPubChecker = {
                val msgText = it.receiveErrorResponse()
                assertTrue("The problem is found in one of the loaded libraries" in msgText)
            },
        )
    }

    @Test
    fun testCounter() {
        fun checkCounter(
            message: Message,
            expectedCounter: Int,
        ) {
            val data = message.data.content as ExecuteSuccessReply
            assertEquals(ExecutionCount(expectedCounter), data.executionCount)
        }
        val res1 = doExecute("41", executeReplyChecker = { checkCounter(it, 1) })
        val res2 = doExecute("42", executeReplyChecker = { checkCounter(it, 2) })
        val res3 =
            doExecute(
                " \"\${Out[1]} \${Out[2]}\" ",
                storeHistory = false,
                executeReplyChecker = { checkCounter(it, 3) },
            )
        val res4 =
            doExecute(
                "val x = try { Out[3] } catch(e: ArrayIndexOutOfBoundsException) { null }; x",
                storeHistory = false,
                executeReplyChecker = { checkCounter(it, 3) },
            )

        assertEquals(jsonObject(MimeTypes.PLAIN_TEXT to "41"), res1)
        assertEquals(jsonObject(MimeTypes.PLAIN_TEXT to "42"), res2)
        assertEquals(jsonObject(MimeTypes.PLAIN_TEXT to "41 42"), res3)
        assertEquals(jsonObject(MimeTypes.PLAIN_TEXT to "null"), res4)
    }

    @Test
    fun testReadLineWithNoStdin() {
        executeWithNoStdin("readLine() ?: \"blah\"")
    }

    @Test
    fun testStdinReadWithNoStdin() {
        executeWithNoStdin("System.`in`.read()")
    }

    @Test
    fun testIsComplete() {
        doIsComplete("2 + 2") shouldBe "complete"

        doIsComplete("fun f() : Int { return 1") shouldBe
            when (replCompilerMode) {
                K1 -> {
                    "incomplete"
                }
                K2 -> {
                    // Modify test until KTNB-916 is fixed
                    "complete"
                }
            }

        // Check that the `isComplete` request turns the logging off
        LogbackLoggingManager(testLoggerFactory).apply {
            isLoggingEnabled() shouldBe runServerInSeparateProcess
        }
    }

    @Test
    fun testLoggerAppender() {
        val file = File.createTempFile("kotlin-jupyter-logger-appender-test", ".txt")
        doExecute("%logHandler add f1 --file ${file.absolutePath}", false)
        val result1 = doExecute("2 + 2")
        assertEquals(jsonObject(MimeTypes.PLAIN_TEXT to "4"), result1)

        doExecute("%logHandler remove f1", false)
        val result2 = doExecute("3 + 4")
        assertEquals(jsonObject(MimeTypes.PLAIN_TEXT to "7"), result2)

        val logText = file.readText()
        logText.shouldContain("2 + 2")
        logText.shouldNotContain("3 + 4")

        file.delete()
    }

    @Test
    fun testComms() {
        val targetName = "my_comm"
        val commId = "xyz"

        val registerCode =
            """
            import kotlinx.serialization.*
            import kotlinx.serialization.json.*

            notebook.commManager.registerCommTarget("$targetName") { comm, openData ->
                comm.send(
                    JsonObject(
                        mapOf(
                            "xo" to JsonPrimitive(comm.id)
                        )
                    )
                )

                comm.onMessage { d ->
                    comm.send(
                        JsonObject(
                            mapOf(
                                "y" to JsonPrimitive("received: " + d["x"]!!.jsonPrimitive.content)
                            )
                        )
                    )
                }
            }
            """.trimIndent()
        doExecute(registerCode, false)

        shellSocket.sendMessage(MessageType.COMM_OPEN, CommOpenMessage(commId, targetName))

        ioPubSocket.receiveMessage().apply {
            val c = content.shouldBeTypeOf<CommMsgMessage>()
            c.commId shouldBe commId
            c.data["xo"]!!.jsonPrimitive.content shouldBe commId
        }

        // Thread.sleep(5000)

        shellSocket.sendMessage(
            MessageType.COMM_MSG,
            CommMsgMessage(
                commId,
                JsonObject(
                    mapOf(
                        "x" to JsonPrimitive("4321"),
                    ),
                ),
            ),
        )

        wrapActionInBusyIdleStatusChange(iopubSocket = ioPubSocket) {
            ioPubSocket.receiveMessage().apply {
                val c = content.shouldBeTypeOf<CommMsgMessage>()
                c.commId shouldBe commId
                c.data["y"]!!.jsonPrimitive.content shouldBe "received: 4321"
            }
        }
    }

    @Test
    fun testDebugPortCommHandler() {
        val targetName = ProvidedCommMessages.OPEN_DEBUG_PORT_TARGET
        val commId = "some"
        val actualDebugPort = kernelConfig.ownParams.debugPort

        shellSocket.sendMessage(
            MessageType.COMM_OPEN,
            CommOpenMessage(
                commId,
                targetName,
            ),
        )

        shellSocket.sendMessage(
            MessageType.COMM_MSG,
            CommMsgMessage(commId),
        )

        wrapActionInBusyIdleStatusChange(iopubSocket = ioPubSocket) {
            ioPubSocket.receiveMessage().apply {
                val c = content.shouldBeTypeOf<CommMsgMessage>()
                val data =
                    MessageFormat.decodeFromJsonElement<OpenDebugPortReply>(c.data).shouldBeTypeOf<OpenDebugPortReply>()
                c.commId shouldBe commId
                data.port shouldBe actualDebugPort
                data.status shouldBe MessageStatus.OK
            }
        }
    }

    @Test
    fun testCommand() {
        val res = doExecute(":help")
        res.shouldBeTypeOf<JsonObject>()
        val text = res[MimeTypes.PLAIN_TEXT]!!.jsonPrimitive.content
        text.shouldContain(currentKotlinVersion)
        print(text)
    }

    @Test
    @EnabledForJreRange(max = JRE.JAVA_19, disabledReason = "Thread.stop() is not supported on JDK 20+")
    fun testInterrupt() {
        doExecute(
            "while(true);",
            hasResult = false,
            executeRequestSent = {
                Thread.sleep(15_000)
                interruptExecution()
            },
            ioPubChecker = { iopubSocket ->
                val msgText = iopubSocket.receiveErrorResponse()
                msgText shouldBe EXECUTION_INTERRUPTED_MESSAGE
            },
        ) shouldBe null

        doExecute("1") shouldBe jsonObject(MimeTypes.PLAIN_TEXT to "1")
        doExecute("2") shouldBe jsonObject(MimeTypes.PLAIN_TEXT to "2")
    }

    @Test
    @Disabled
    fun testBigDataFrame() {
        doExecute(
            """
            %use dataframe
            DataFrame.read("https://api.apis.guru/v2/list.json")
            """.trimIndent(),
            ioPubChecker = { iopubSocket ->
                iopubSocket.receiveDisplayDataResponse()
                iopubSocket.receiveDisplayDataResponse()
            },
        )
    }

    // Verify we do not crash when sending UPDATE_DISPLAY messages
    @Test
    fun testUpdateDisplay() {
        doExecute(
            """
            UPDATE_DISPLAY("b", "id1")
            "hello"
            """.trimIndent(),
            ioPubChecker = { iopubSocket ->
                // In case of an error, this would return STREAM
                iopubSocket.receiveUpdateDisplayDataResponse()
            },
        )
    }

    @Test
    fun testExecuteExceptionInSameCell() {
        val code =
            """
            val foo = "bar"
            TODO()
            """.trimIndent()
        doExecute(
            code,
            hasResult = false,
            ioPubChecker = { ioPubSocket ->
                // If execution throws an exception, we should send an ERROR
                // message type with the appropriate metadata.
                val message = ioPubSocket.receiveMessage()
                val content = message.content
                content.shouldBeTypeOf<ExecuteErrorReply>()
                content.name shouldBe "kotlin.NotImplementedError"
                content.value shouldBe "An operation is not implemented."

                // Stacktrace should be enhanced with cell information
                when (replCompilerMode) {
                    K1 -> {
                        content.traceback shouldContain "\tat Line_0_jupyter.<init>(Line_0.jupyter.kts:2) at Cell In[1], line 2"
                        content.traceback.last() shouldBe "at Cell In[1], line 2"
                    }
                    K2 -> {
                        content.traceback shouldContain "\tat Line_0_jupyter.\$\$eval(Line_0.jupyter.kts:2) at Cell In[1], line 2"
                        content.traceback.last() shouldBe "at Cell In[1], line 2"
                    }
                }
            },
        )
    }

    @Test
    fun testExecuteExceptionInOtherCell() {
        doExecute(
            """
            val callback: (Int) -> Int = { i: Int ->
                if (i == 42) TODO()
                else i
            }
            """.trimIndent(),
            hasResult = false,
        )
        val code =
            """
            callback(42)
            """.trimIndent()
        doExecute(
            code,
            hasResult = false,
            ioPubChecker = { ioPubSocket: JupyterReceiveSocket ->
                // If execution throws an exception, we should send an ERROR
                // message type with the appropriate metadata.
                val message = ioPubSocket.receiveMessage()
                message.type shouldBe MessageType.ERROR
                val content = message.content
                content.shouldBeTypeOf<ExecuteErrorReply>()
                content.name shouldBe "kotlin.NotImplementedError"
                content.value shouldBe "An operation is not implemented."

                // Stacktrace should be enhanced with cell information
                when (replCompilerMode) {
                    K1 -> {
                        content.traceback shouldContain
                            $$"\tat Line_0_jupyter.callback$lambda$0(Line_0.jupyter.kts:2) at Cell In[1], line 2"
                        content.traceback shouldContain "\tat Line_1_jupyter.<init>(Line_1.jupyter.kts:1) at Cell In[2], line 1"
                        content.traceback.last() shouldBe "at Cell In[1], line 2"
                    }
                    K2 -> {
                        content.traceback shouldContain $$"\tat Line_0_jupyter.__eval$lambda$0(Line_0.jupyter.kts:2) at Cell In[1], line 2"
                        content.traceback shouldContain $$$"\tat Line_1_jupyter.$$eval(Line_1.jupyter.kts:1) at Cell In[2], line 1"
                        content.traceback.last() shouldBe "at Cell In[1], line 2"
                    }
                }
            },
        )
    }

    // In case of an exception happening in generated code. This code isn't visible to
    // the user. In that case, these things happen:
    // - Any stack trace line that references outside the visible code range only shows the request count.
    // - We find the first "visible" error and promote that at the end of error output instead of the
    //   first error. This means that the user can hopefully find the point in their code that triggers
    //   the behavior
    // - If no visible user code can be found, only the request count is displayed but no line number.
    @Test
    fun testExceptionInGeneratedCodeShouldNotReferenceLine() {
        // Waiting for https://youtrack.jetbrains.com/issue/KT-75580/K2-Repl-Cannot-access-snippet-properties-using-Kotlin-reflection
        val code =
            """
            %use ktor-client

            @Serializable
            class User(val id: Int)

            // Body cannot be serialized, and will throw exception in generated code
            http.get("https://github.com/Kotlin/kotlin-jupyter").body<User>()
            """.trimIndent()
        doExecute(
            code,
            hasResult = false,
            ioPubChecker = { ioPubSocket ->
                val message = ioPubSocket.receiveMessage()
                message.type shouldBe MessageType.ERROR
                val content = message.content
                content.shouldBeTypeOf<ExecuteErrorReply>()
                when (replCompilerMode) {
                    K1 -> {
                        content.name shouldBe "io.ktor.serialization.JsonConvertException"
                        content.value shouldBe
                            "Illegal input: Field 'id' is required for type with serial name " +
                            "'Line_6_jupyter.User', but it was missing at path: $"
                    }
                    K2 -> {
                        // See https://youtrack.jetbrains.com/issue/KT-75672/K2-Repl-Serialization-plugin-crashes-compiler-backend
                        content.name shouldBe "org.jetbrains.kotlinx.jupyter.exceptions.ReplInterruptedException"
                    }
                }
            },
        )
    }

    @Test
    fun testCompileErrorsAreEnhanced() {
        val illegalCode =
            """
            val var
            """.trimIndent()
        doExecute(
            illegalCode,
            hasResult = false,
            ioPubChecker = { ioPubSocket ->
                // If execution throws an exception, we should send an ERROR
                // message type with the appropriate metadata.
                val message = ioPubSocket.receiveMessage()
                message.type shouldBe MessageType.ERROR
                val content = message.content
                content.shouldBeTypeOf<ExecuteErrorReply>()
                content.name shouldBe "org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException"
                when (replCompilerMode) {
                    K1 -> {
                        content.value shouldBe
                            """
                            at Cell In[1], line 1, column 4: Expecting property name or receiver type
                            at Cell In[1], line 1, column 4: Property getter or setter expected
                            at Cell In[1], line 1, column 8: Expecting property name or receiver type
                            """.trimIndent()
                    }
                    K2 -> {
                        content.value shouldBe
                            """
                            at Cell In[1], line 1, column 4: Expecting property name or receiver type
                            at Cell In[1], line 1, column 4: Property getter or setter expected
                            at Cell In[1], line 1, column 8: Expecting property name or receiver type
                            at Cell In[1], line 1, column 1: This variable must either have an explicit type or be initialized.
                            at Cell In[1], line 1, column 5: This variable must either have an explicit type or be initialized.
                            """.trimIndent()
                    }
                }
                // Error should also contain the stack trace
                content.traceback.size shouldBeGreaterThan 0
            },
        )
    }

    @Test
    fun testLibraryExceptionShouldContainFullStackTrace() {
        val code =
            """
            USE {
                dependencies {
                    implementation("some:nonexistent:lib")
                }
            }
            """.trimIndent()
        doExecute(
            code,
            hasResult = false,
            ioPubChecker = { ioPubSocket ->
                // If execution throws an exception, we should send an ERROR
                // message type with the appropriate metadata.
                val message = ioPubSocket.receiveMessage()
                message.type shouldBe MessageType.ERROR
                val content = message.content
                content.shouldBeTypeOf<ExecuteErrorReply>()
                content.name shouldBe "org.jetbrains.kotlinx.jupyter.exceptions.ReplLibraryException"
                content.value shouldBe
                    "The problem is found in one of the loaded libraries: check library imports, dependencies and repositories"
                // Error should also contain the stack trace
                content.traceback.size shouldBeGreaterThan 0
            },
        )
    }

    @Test
    fun `kernel_info_reply should contain info about session state`() {
        doExecute(
            """
            SessionOptions.serializeScriptData = true
            """.trimIndent(),
            hasResult = false,
        )

        doExecute(
            """
            %use ktor-client
            """.trimIndent(),
            hasResult = false,
        )

        doExecute(
            """
            import java.util.concurrent.ConcurrentHashMap

            buildJsonObject {
                put("a", JsonPrimitive(1))
                put("b", JsonPrimitive(2))
            }
            """.trimIndent(),
        )

        val infoResponse = requestKernelInfo()
        val metadataObject = infoResponse.data.metadata
        metadataObject.shouldBeTypeOf<JsonObject>()
        val metadata = MessageFormat.decodeFromJsonElement<KernelInfoReplyMetadata>(metadataObject)

        with(metadata.state) {
            assertTrue { newClasspath.any { "kotlin-jupyter-ktor-client" in it } }
            newImports shouldContain "org.jetbrains.kotlinx.jupyter.serialization.UntypedAny"
            newImports shouldContain "java.util.concurrent.ConcurrentHashMap"

            compiledData.scripts.shouldNotBeEmpty()
        }
    }

    @Test
    fun `update client metadata`() {
        // Users should never see this state
        doExecute("notebook.workingDir.toString()") shouldBe jsonObject(MimeTypes.PLAIN_TEXT to "")

        val tempFile = Paths.get("", "notebook.ipynb").toAbsolutePath()
        sendClientMetadata(tempFile)

        doExecute("notebook.workingDir.toString()".trimIndent()) shouldBe jsonObject(MimeTypes.PLAIN_TEXT to tempFile.parent.pathString)
    }

    @Test
    fun testOutputFromMultipleThreads() {
        val code =
            """
            import kotlin.concurrent.thread

            println("main thread")
            thread(start = true) {
                println("something should be printed")
            }.join()
            """.trimIndent()

        fun checker(ioPub: JupyterReceiveSocket) {
            val expectedText = "main thread\nsomething should be printed\n"
            val actualText = StringBuilder()

            while (true) {
                val msg = ioPub.receiveMessage()
                assertEquals(MessageType.STREAM, msg.type)
                actualText.append((msg.content as StreamMessage).text)
                assertStartsWith(actualText, expectedText)
                if (actualText.contentEquals(expectedText)) break
            }
        }

        val res = doExecute(code, false, ::checker)
        assertNull(res)
    }

    // Test for https://youtrack.jetbrains.com/issue/KT-76508/K2-Repl-Annotations-on-property-accessors-are-not-resolved
    @Test
    fun testPropertyAnnotations() {
        val code =
            """
            val test
                @JvmName("customGetter")
                get() = "Hello"
            test
            """.trimIndent()
        when (replCompilerMode) {
            K1 -> {
                val res = doExecute(code) as JsonObject
                assertEquals("Hello", res.string(MimeTypes.PLAIN_TEXT))
            }
            K2 -> {
                // This should be fixed by KT-76508
                val res = doExecute(code) as JsonObject
                res["text/plain"]?.jsonPrimitive?.content shouldBe "null"
            }
        }
    }

    @Test
    fun `loggerFactory should be accessible`() {
        val logger =
            doExecute(
                """
                val LOG = notebook.loggerFactory.getLogger("My debug logger")
                LOG
                """.trimIndent(),
            )
        logger shouldBe jsonObject(MimeTypes.PLAIN_TEXT to "Logger[My debug logger]")
        doExecute("LOG.debug(\"Debug message\")", hasResult = false)
    }
}

class ExecuteZmqTests :
    ExecuteTests(
        socketManager = JupyterZmqClientReceiveSocketManager(testLoggerFactory),
        generatePorts = ::createRandomZmqKernelPorts,
    )

class ExecuteWsTests :
    ExecuteTests(
        socketManager = JupyterWsClientReceiveSocketManager(testLoggerFactory),
        generatePorts = { WsKernelPorts(PortsGenerator.create(32768, 65536).randomPort()) },
    )

private const val CONNECT_RETRY_TIMEOUT_SECONDS = 30
