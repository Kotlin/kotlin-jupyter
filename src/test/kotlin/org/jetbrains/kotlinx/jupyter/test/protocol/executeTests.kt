package org.jetbrains.kotlinx.jupyter.test.protocol

import ch.qos.logback.classic.Level.DEBUG
import ch.qos.logback.classic.Level.OFF
import io.kotest.matchers.paths.shouldBeAFile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import jupyter.kotlin.providers.UserHandlesProvider
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlinx.jupyter.LoggingManagement.mainLoggerLevel
import org.jetbrains.kotlinx.jupyter.api.MimeTypes
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.SessionOptions
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.config.currentKotlinVersion
import org.jetbrains.kotlinx.jupyter.messaging.CommMsg
import org.jetbrains.kotlinx.jupyter.messaging.CommOpen
import org.jetbrains.kotlinx.jupyter.messaging.DisplayDataResponse
import org.jetbrains.kotlinx.jupyter.messaging.EXECUTION_INTERRUPTED_MESSAGE
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteReply
import org.jetbrains.kotlinx.jupyter.messaging.ExecuteRequest
import org.jetbrains.kotlinx.jupyter.messaging.ExecutionResultMessage
import org.jetbrains.kotlinx.jupyter.messaging.InputReply
import org.jetbrains.kotlinx.jupyter.messaging.InterruptRequest
import org.jetbrains.kotlinx.jupyter.messaging.IsCompleteReply
import org.jetbrains.kotlinx.jupyter.messaging.IsCompleteRequest
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
import org.jetbrains.kotlinx.jupyter.test.NotebookMock
import org.jetbrains.kotlinx.jupyter.test.assertStartsWith
import org.jetbrains.kotlinx.jupyter.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
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
class ExecuteTests : KernelServerTestsBase() {
    private var _context: ZMQ.Context? = null
    override val context: ZMQ.Context
        get() = _context!!

    private var shell: JupyterSocket? = null
    private var control: JupyterSocket? = null
    private var ioPub: JupyterSocket? = null
    private var stdin: JupyterSocket? = null

    override fun beforeEach() {
        try {
            _context = ZMQ.context(1)
            shell = createClientSocket(JupyterSocketInfo.SHELL).apply {
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
            val shell = this.shell!!
            val ioPub = this.ioPub!!
            val stdin = this.stdin!!
            shell.sendMessage(MessageType.EXECUTE_REQUEST, content = ExecuteRequest(code, allowStdin = allowStdin, storeHistory = storeHistory))
            executeRequestSent()
            inputs.forEach {
                stdin.sendMessage(MessageType.INPUT_REPLY, InputReply(it))
            }

            var msg = shell.receiveMessage()
            assertEquals(MessageType.EXECUTE_REPLY, msg.type)
            executeReplyChecker(msg)

            msg = ioPub.receiveMessage()
            assertEquals(MessageType.STATUS, msg.type)
            assertEquals(KernelStatus.BUSY, (msg.content as StatusReply).status)
            msg = ioPub.receiveMessage()
            assertEquals(MessageType.EXECUTE_INPUT, msg.type)

            ioPubChecker(ioPub)

            var response: Any? = null
            if (hasResult) {
                msg = ioPub.receiveMessage()
                val content = msg.content as ExecutionResultMessage
                assertEquals(MessageType.EXECUTE_RESULT, msg.type)
                response = content.data
            }

            msg = ioPub.receiveMessage()
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
                assertEquals(MessageType.STREAM, msg.type)
                assertStartsWith("Input from stdin is unsupported by the client", (msg.content as StreamResponse).text)
            },
        )
    }

    private fun doIsComplete(code: String): String {
        try {
            val shell = this.shell!!
            shell.sendMessage(MessageType.IS_COMPLETE_REQUEST, content = IsCompleteRequest(code))

            val responseMsg = shell.receiveMessage()
            assertEquals(MessageType.IS_COMPLETE_REPLY, responseMsg.type)

            val content = responseMsg.content as IsCompleteReply
            return content.status
        } catch (e: Throwable) {
            afterEach()
            throw e
        }
    }

    private fun interruptExecution() {
        control!!.sendMessage(MessageType.INTERRUPT_REQUEST, InterruptRequest())
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

    private fun JupyterSocket.receiveDisplayDataResponse(): DisplayDataResponse {
        return receiveMessageOfType(MessageType.DISPLAY_DATA)
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

    // TODO: investigate, why this test is hanging
    @Test
    fun testReadLine() {
        val code =
            """
            val answer = readLine()
            answer
            """.trimIndent()
        val res = doExecute(code, inputs = listOf("42"))
        assertEquals(jsonObject(MimeTypes.PLAIN_TEXT to "42"), res)
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
        val res = doExecute(
            code,
            hasResult = false,
            executeReplyChecker = { message ->
                val metadata = message.data.metadata
                assertTrue(metadata is JsonObject)
                val snippetMetadata = MessageFormat.decodeFromJsonElement<EvaluatedSnippetMetadata?>(
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

                val userHandlesProvider = object : UserHandlesProvider {
                    override val host: Nothing? = null
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
                val msgText = it.receiveStreamResponse()
                assertTrue("The problem is found in one of the loaded libraries" in msgText)
            },
        )
    }

    @Test
    fun testCounter() {
        fun checkCounter(message: Message, expectedCounter: Long) {
            val data = message.data.content as ExecuteReply
            assertEquals(expectedCounter, data.executionCount)
        }
        val res1 = doExecute("41", executeReplyChecker = { checkCounter(it, 1) })
        val res2 = doExecute("42", executeReplyChecker = { checkCounter(it, 2) })
        val res3 = doExecute(
            " \"\${Out[1]} \${Out[2]}\" ",
            storeHistory = false,
            executeReplyChecker = { checkCounter(it, 3) },
        )
        val res4 = doExecute(
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
        assertEquals(if (runInSeparateProcess) DEBUG else OFF, mainLoggerLevel())
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
        assertTrue("2 + 2" in logText)
        assertTrue("3 + 4" !in logText)

        file.delete()
    }

    @Test
    fun testComms() {
        val shell = shell!!
        val iopub = ioPub!!

        val targetName = "my_comm"
        val commId = "xyz"

        val registerCode = """
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

        shell.sendMessage(MessageType.COMM_OPEN, CommOpen(commId, targetName))

        iopub.receiveMessage().apply {
            val c = content.shouldBeTypeOf<CommMsg>()
            c.commId shouldBe commId
            c.data["xo"]!!.jsonPrimitive.content shouldBe commId
        }

        // Thread.sleep(5000)

        shell.sendMessage(
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

        iopub.wrapActionInBusyIdleStatusChange {
            iopub.receiveMessage().apply {
                val c = content.shouldBeTypeOf<CommMsg>()
                c.commId shouldBe commId
                c.data["y"]!!.jsonPrimitive.content shouldBe "received: 4321"
            }
        }
    }

    @Test
    fun testDebugPortCommHandler() {
        val shell = shell!!
        val iopub = ioPub!!

        val targetName = ProvidedCommMessages.OPEN_DEBUG_PORT_TARGET
        val commId = "some"
        val actualDebugPort = kernelConfig.debugPort

        shell.sendMessage(
            MessageType.COMM_OPEN,
            CommOpen(
                commId,
                targetName,
            ),
        )

        shell.sendMessage(
            MessageType.COMM_MSG,
            CommMsg(commId),
        )

        iopub.wrapActionInBusyIdleStatusChange {
            iopub.receiveMessage().apply {
                val c = content.shouldBeTypeOf<CommMsg>()
                val data = MessageFormat.decodeFromJsonElement<OpenDebugPortReply>(c.data).shouldBeTypeOf<OpenDebugPortReply>()
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
    fun testInterrupt() {
        doExecute(
            "while(true);",
            hasResult = false,
            executeRequestSent = {
                Thread.sleep(15000)
                interruptExecution()
            },
            ioPubChecker = { iopubSocket ->
                val msgText = iopubSocket.receiveStreamResponse()
                msgText shouldBe EXECUTION_INTERRUPTED_MESSAGE
            },
        ) shouldBe null
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
}
