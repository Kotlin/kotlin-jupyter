package org.jetbrains.kotlinx.jupyter.test

import jupyter.kotlin.KotlinKernelHostProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.kotlinx.jupyter.ExecuteReply
import org.jetbrains.kotlinx.jupyter.ExecuteRequest
import org.jetbrains.kotlinx.jupyter.ExecutionResult
import org.jetbrains.kotlinx.jupyter.InputReply
import org.jetbrains.kotlinx.jupyter.JupyterSockets
import org.jetbrains.kotlinx.jupyter.KernelStatus
import org.jetbrains.kotlinx.jupyter.Message
import org.jetbrains.kotlinx.jupyter.MessageType
import org.jetbrains.kotlinx.jupyter.StatusReply
import org.jetbrains.kotlinx.jupyter.StreamResponse
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScriptsData
import org.jetbrains.kotlinx.jupyter.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.zeromq.ZMQ
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

fun JsonObject.string(key: String): String {
    return (get(key) as JsonPrimitive).content
}

@Timeout(100, unit = TimeUnit.SECONDS)
class ExecuteTests : KernelServerTestsBase() {
    private var context: ZMQ.Context? = null
    private var shell: ClientSocket? = null
    private var ioPub: ClientSocket? = null
    private var stdin: ClientSocket? = null

    override fun beforeEach() {
        try {
            context = ZMQ.context(1)
            shell = ClientSocket(context!!, JupyterSockets.SHELL)
            ioPub = ClientSocket(context!!, JupyterSockets.IOPUB)
            stdin = ClientSocket(context!!, JupyterSockets.STDIN)
            ioPub?.subscribe(byteArrayOf())
            shell?.connect()
            ioPub?.connect()
            stdin?.connect()
        } catch (e: Throwable) {
            afterEach()
            throw e
        }
    }

    override fun afterEach() {
        shell?.close()
        shell = null
        ioPub?.close()
        ioPub = null
        stdin?.close()
        stdin = null
        context?.term()
        context = null
    }

    private fun doExecute(
        code: String,
        hasResult: Boolean = true,
        ioPubChecker: (ZMQ.Socket) -> Unit = {},
        executeReplyChecker: (Message) -> Unit = {},
        inputs: List<String> = emptyList(),
        allowStdin: Boolean = true,
    ): Any? {
        try {
            val shell = this.shell!!
            val ioPub = this.ioPub!!
            val stdin = this.stdin!!
            shell.sendMessage(MessageType.EXECUTE_REQUEST, content = ExecuteRequest(code))
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
                val content = msg.content as ExecutionResult
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

    private fun testWithNoStdin(code: String) {
        doExecute(
            code,
            hasResult = false,
            allowStdin = false,
            ioPubChecker = {
                val msg = it.receiveMessage()
                assertEquals("stream", msg.type())
                assertTrue((msg.content!!["text"] as String).startsWith("java.io.IOException: Input from stdin is unsupported by the client"))
            }
        )
    }

    @Test
    fun testExecute() {
        val res = doExecute("2+2") as JsonObject
        assertEquals("4", res.string("text/plain"))
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

        fun checker(ioPub: ZMQ.Socket) {
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

        fun checker(ioPub: ZMQ.Socket) {
            for (el in expected) {
                val msg = ioPub.receiveMessage()
                val content = msg.content
                assertEquals(MessageType.STREAM, msg.type)
                assertTrue(content is StreamResponse)
                assertEquals(el, content.text)
            }
        }

        val res = doExecute(code, false, ::checker)
        assertNull(res)
    }

    @Test
    fun testOutputStrings() {
        val code =
            """
            for (i in 1..5) {
                Thread.sleep(200)
                println("text" + i)
            }
            """.trimIndent()

        fun checker(ioPub: ZMQ.Socket) {
            for (i in 1..5) {
                val msg = ioPub.receiveMessage()
                assertEquals(MessageType.STREAM, msg.type)
                assertEquals("text$i" + System.lineSeparator(), (msg.content as StreamResponse).text)
            }
        }

        val res = doExecute(code, false, ::checker)
        assertNull(res)
    }

    @Test
    // TODO: investigate, why this test is hanging
    fun testReadLine() {
        val code =
            """
            val answer = readLine()
            answer
            """.trimIndent()
        val res = doExecute(code, inputs = listOf("42"))
        assertEquals(jsonObject("text/plain" to "42"), res)
    }

    @Test
    fun testCompiledData() {
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
                val compiledData = Json.decodeFromJsonElement<SerializedCompiledScriptsData?>(
                    metadata["compiled_data"] ?: JsonNull
                )
                assertNotNull(compiledData)

                val deserializer = org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer()
                val dir = Files.createTempDirectory("kotlin-jupyter-exec-test")

                val names = deserializer.deserializeAndSave(compiledData, dir)
                val kClassName = names.single()
                val classLoader = URLClassLoader(arrayOf(dir.toUri().toURL()), ClassLoader.getSystemClassLoader())
                val loadedClass = classLoader.loadClass(kClassName).kotlin
                dir.toFile().delete()

                @Suppress("UNCHECKED_CAST")
                val xyzProperty = loadedClass.memberProperties.single { it.name == "xyz" } as KProperty1<Any, Int>
                val constructor = loadedClass.constructors.single()

                val hostProvider = object : KotlinKernelHostProvider {
                    override val host = null
                }

                val instance = constructor.call(NotebookMock(), hostProvider)

                val result = xyzProperty.get(instance)
                assertEquals(42, result)
            }
        )
        assertNull(res)
    }

    @Test
    fun testCounter() {
        fun checkCounter(message: Message, expectedCounter: Long) {
            val data = message.data.content as ExecuteReply
            assertEquals(expectedCounter, data.executionCount)
        }
        val res1 = doExecute("42", executeReplyChecker = { checkCounter(it, 1) })
        val res2 = doExecute("43", executeReplyChecker = { checkCounter(it, 2) })

        assertEquals(jsonObject("text/plain" to "42"), res1)
        assertEquals(jsonObject("text/plain" to "43"), res2)
    }

    @Test
    fun testReadLineWithNoStdin() {
        testWithNoStdin("readLine() ?: \"blah\"")
    }

    @Test
    fun testStdinReadWithNoStdin() {
        testWithNoStdin("System.`in`.read()")
    }
}
