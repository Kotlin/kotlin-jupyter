package org.jetbrains.kotlinx.jupyter.test.protocol

import ch.qos.logback.classic.Level.DEBUG
import ch.qos.logback.classic.Level.OFF
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.paths.shouldBeAFile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeTypeOf
import jupyter.kotlin.providers.UserHandlesProvider
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlinx.jupyter.LoggingManager
import org.jetbrains.kotlinx.jupyter.api.MimeTypes
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.SessionOptions
import org.jetbrains.kotlinx.jupyter.config.currentKotlinVersion
import org.jetbrains.kotlinx.jupyter.messaging.CommMsg
import org.jetbrains.kotlinx.jupyter.messaging.CommOpen
import org.jetbrains.kotlinx.jupyter.messaging.DisplayDataResponse
import org.jetbrains.kotlinx.jupyter.messaging.EXECUTION_INTERRUPTED_MESSAGE
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteErrorReply
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteRequest
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteSuccessReply
import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCount
import org.jetbrains.kotlinx.jupyter.messaging.ExecutionResultMessage
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
import org.jetbrains.kotlinx.jupyter.messaging.StatusReply
import org.jetbrains.kotlinx.jupyter.messaging.StreamResponse
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketInfo
import org.jetbrains.kotlinx.jupyter.protocol.MessageFormat
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.test.NotebookMock
import org.jetbrains.kotlinx.jupyter.test.assertStartsWith
import org.jetbrains.kotlinx.jupyter.test.testLoggerFactory
import org.jetbrains.kotlinx.jupyter.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.zeromq.ZMQ
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

fun JsonObject.string(key: String): String {
    return (get(key) as JsonPrimitive).content
}

@Timeout(100, unit = TimeUnit.SECONDS)
@Execution(ExecutionMode.SAME_THREAD)
class ExecuteTests : KernelServerTestsBase(runServerInSeparateProcess = true) {
    private var _context: ZMQ.Context? = null
    override val context: ZMQ.Context
        get() = _context!!

    private var shell: JupyterSocket? = null
    private var control: JupyterSocket? = null
    private var ioPub: JupyterSocket? = null
    private var stdin: JupyterSocket? = null

    private val shellSocket get() = shell!!
    private val controlSocket get() = control!!
    private val ioPubSocket get() = ioPub!!
    private val stdinSocket get() = stdin!!

    override fun beforeEach() {
        try {
            _context = ZMQ.context(1)
            shell =
                createClientSocket(JupyterSocketInfo.SHELL).apply {
                    makeRelaxed()
                }
            ioPub = createClientSocket(JupyterSocketInfo.IOPUB)
            stdin = createClientSocket(JupyterSocketInfo.STDIN)
            control = createClientSocket(JupyterSocketInfo.CONTROL)

            ioPub?.subscribe(byteArrayOf())
            shell?.connect()
            ioPub?.connect()
            stdin?.connect()
            control?.connect()
        } catch (e: Throwable) {
            afterEach()
            throw e
        }
    }

    override fun afterEach() {
        listOf(::shell, ::ioPub, ::stdin, ::control).forEach { socketProp ->
            socketProp.get()?.close()
            socketProp.set(null)
        }
        context.term()
        _context = null
    }

    private fun doExecute(
        code: String,
        hasResult: Boolean = true,
        ioPubChecker: (JupyterSocket) -> Unit = {},
        executeRequestSent: () -> Unit = {},
        executeReplyChecker: (Message) -> Unit = {},
        inputs: List<String> = emptyList(),
        allowStdin: Boolean = true,
        storeHistory: Boolean = true,
    ): Any? {
        try {
            shellSocket.sendMessage(
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

            msg = ioPubSocket.receiveMessage()
            assertEquals(MessageType.STATUS, msg.type)
            assertEquals(KernelStatus.BUSY, (msg.content as StatusReply).status)
            msg = ioPubSocket.receiveMessage()
            assertEquals(MessageType.EXECUTE_INPUT, msg.type)

            ioPubChecker(ioPubSocket)

            var response: Any? = null
            if (hasResult) {
                msg = ioPubSocket.receiveMessage()
                val content = msg.content as ExecutionResultMessage
                assertEquals(MessageType.EXECUTE_RESULT, msg.type)
                response = content.data
            }

            msg = ioPubSocket.receiveMessage()
            assertEquals(MessageType.STATUS, msg.type)
            assertEquals(KernelStatus.IDLE, (msg.content as StatusReply).status)
            return response
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
        val responseMsg = shellSocket.receiveMessage()
        responseMsg.type shouldBe MessageType.KERNEL_INFO_REPLY
        val content = responseMsg.content
        content.shouldBeTypeOf<KernelInfoReply>()
        return responseMsg
    }

    private inline fun <reified T : Any> JupyterSocket.receiveMessageOfType(messageType: MessageType): T {
        val msg = receiveMessage()
        assertEquals(messageType, msg.type)
        val content = msg.content
        content.shouldBeTypeOf<T>()
        return content
    }

    private fun JupyterSocket.receiveStreamResponse(): String {
        return receiveMessageOfType<StreamResponse>(MessageType.STREAM).text
    }

    private fun JupyterSocket.receiveErrorResponse(): String {
        return receiveMessageOfType<ExecuteErrorReply>(MessageType.ERROR).value
    }

    private fun JupyterSocket.receiveDisplayDataResponse(): DisplayDataResponse {
        return receiveMessageOfType(MessageType.DISPLAY_DATA)
    }

    private fun JupyterSocket.receiveUpdateDisplayDataResponse(): DisplayDataResponse {
        return receiveMessageOfType(MessageType.UPDATE_DISPLAY_DATA)
    }

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

        fun checker(ioPub: JupyterSocket) {
            for (i in 1..5) {
                val msg = ioPub.receiveMessage()
                assertEquals(MessageType.STREAM, msg.type)
                assertEquals(i.toString(), (msg.content as StreamResponse).text)
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

        fun checker(ioPub: JupyterSocket) {
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

        fun checker(ioPub: JupyterSocket) {
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

                    val deserializer = org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer()
                    val dir = Files.createTempDirectory("kotlin-jupyter-exec-test")
                    dir.toFile().deleteOnExit()
                    val classesDir = dir.resolve("classes")
                    val sourcesDir = dir.resolve("sources")

                    val names = deserializer.deserializeAndSave(compiledData, classesDir, sourcesDir)
                    val kClassName = names.single()
                    val classLoader = URLClassLoader(arrayOf(classesDir.toUri().toURL()), ClassLoader.getSystemClassLoader())
                    val loadedClass = classLoader.loadClass(kClassName).kotlin

                    @Suppress("UNCHECKED_CAST")
                    val xyzProperty = loadedClass.memberProperties.single { it.name == "xyz" } as KProperty1<Any, Int>
                    val constructor = loadedClass.constructors.single()

                    val userHandlesProvider =
                        object : UserHandlesProvider {
                            override val notebook: Notebook = NotebookMock
                            override val sessionOptions: SessionOptions
                                get() = throw NotImplementedError()
                        }

                    val instance = constructor.call(emptyArray<Any>(), userHandlesProvider)
                    xyzProperty.get(instance) shouldBe 42

                    val sourceFile = sourcesDir.resolve("Line_1.kts")
                    sourceFile.shouldBeAFile()
                    sourceFile.readText() shouldBe "val xyz = 42"
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
        assertEquals("complete", doIsComplete("2 + 2"))
        assertEquals("incomplete", doIsComplete("fun f() : Int { return 1"))
        val loggingManager = LoggingManager(testLoggerFactory)
        assertEquals(if (runServerInSeparateProcess) DEBUG else OFF, loggingManager.mainLoggerLevel())
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

        shellSocket.sendMessage(MessageType.COMM_OPEN, CommOpen(commId, targetName))

        ioPubSocket.receiveMessage().apply {
            val c = content.shouldBeTypeOf<CommMsg>()
            c.commId shouldBe commId
            c.data["xo"]!!.jsonPrimitive.content shouldBe commId
        }

        // Thread.sleep(5000)

        shellSocket.sendMessage(
            MessageType.COMM_MSG,
            CommMsg(
                commId,
                JsonObject(
                    mapOf(
                        "x" to JsonPrimitive("4321"),
                    ),
                ),
            ),
        )

        ioPubSocket.wrapActionInBusyIdleStatusChange {
            ioPubSocket.receiveMessage().apply {
                val c = content.shouldBeTypeOf<CommMsg>()
                c.commId shouldBe commId
                c.data["y"]!!.jsonPrimitive.content shouldBe "received: 4321"
            }
        }
    }

    @Test
    fun testDebugPortCommHandler() {
        val targetName = ProvidedCommMessages.OPEN_DEBUG_PORT_TARGET
        val commId = "some"
        val actualDebugPort = kernelConfig.debugPort

        shellSocket.sendMessage(
            MessageType.COMM_OPEN,
            CommOpen(
                commId,
                targetName,
            ),
        )

        shellSocket.sendMessage(
            MessageType.COMM_MSG,
            CommMsg(commId),
        )

        ioPubSocket.wrapActionInBusyIdleStatusChange {
            ioPubSocket.receiveMessage().apply {
                val c = content.shouldBeTypeOf<CommMsg>()
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
                content.traceback shouldContain "\tat Line_0_jupyter.<init>(Line_0.jupyter.kts:2) at Cell In[1], line 2"
                content.traceback[content.traceback.size - 2] shouldBe "at Cell In[1], line 2"
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
            ioPubChecker = { ioPubSocket: JupyterSocket ->
                // If execution throws an exception, we should send an ERROR
                // message type with the appropriate metadata.
                val message = ioPubSocket.receiveMessage()
                message.type shouldBe MessageType.ERROR
                val content = message.content
                content.shouldBeTypeOf<ExecuteErrorReply>()
                content.name shouldBe "kotlin.NotImplementedError"
                content.value shouldBe "An operation is not implemented."

                // Stacktrace should be enhanced with cell information
                content.traceback shouldContain "\tat Line_0_jupyter\$callback\$1.invoke(Line_0.jupyter.kts:2) at Cell In[1], line 2"
                content.traceback shouldContain "\tat Line_0_jupyter\$callback\$1.invoke(Line_0.jupyter.kts:1) at Cell In[1], line 1"
                content.traceback shouldContain "\tat Line_1_jupyter.<init>(Line_1.jupyter.kts:1) at Cell In[2], line 1"
                content.traceback[content.traceback.size - 2] shouldBe "at Cell In[1], line 2"
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
            ioPubChecker = { ioPubSocket: JupyterSocket ->
                val message = ioPubSocket.receiveMessage()
                message.type shouldBe MessageType.ERROR
                val content = message.content
                content.shouldBeTypeOf<ExecuteErrorReply>()
                content.name shouldBe "io.ktor.serialization.JsonConvertException"
                content.value shouldBe
                    "Illegal input: Field 'id' is required for type with serial name 'Line_6_jupyter.User', but it was missing at path: \$"

                // Stacktrace should only contain the cell reference if error is outside visible range
                content.traceback shouldContain "\tat Line_6_jupyter.<init>(Line_6.jupyter.kts:12) at Cell In[1]"
                content.traceback shouldContain "\tat Line_6_jupyter\$User.<init>(Line_6.jupyter.kts:3) at Cell In[1], line 3"
                content.traceback[content.traceback.size - 2] shouldBe "at Cell In[1]"
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
            ioPubChecker = { ioPubSocket: JupyterSocket ->
                // If execution throws an exception, we should send an ERROR
                // message type with the appropriate metadata.
                val message = ioPubSocket.receiveMessage()
                message.type shouldBe MessageType.ERROR
                val content = message.content
                content.shouldBeTypeOf<ExecuteErrorReply>()
                content.name shouldBe "org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException"
                content.value shouldBe
                    """
                    at Cell In[1], line 1, column 4: Expecting property name or receiver type
                    at Cell In[1], line 1, column 5: Property getter or setter expected
                    """.trimIndent()
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
            ioPubChecker = { ioPubSocket: JupyterSocket ->
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
}
