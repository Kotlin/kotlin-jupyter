package org.jetbrains.kotlinx.jupyter.test

import org.jetbrains.kotlinx.jupyter.protocol.CapturingOutputStream
import org.jetbrains.kotlinx.jupyter.repl.OutputConfig
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.OutputStream
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicInteger

class CapturingStreamTests {
    private val nullOStream = object : OutputStream() {
        override fun write(b: Int) {
        }
    }

    private fun getStream(
        stdout: OutputStream = nullOStream,
        captureOutput: Boolean = true,
        maxBufferLifeTimeMs: Long = 1000,
        maxBufferSize: Int = 1000,
        maxOutputSize: Int = 1000,
        maxBufferNewlineSize: Int = 1,
        onCaptured: (String) -> Unit = {},
    ): CapturingOutputStream {
        val printStream = PrintStream(stdout, false, "UTF-8")
        val config = OutputConfig(captureOutput, maxBufferLifeTimeMs, maxBufferSize, maxOutputSize, maxBufferNewlineSize)
        return CapturingOutputStream(printStream, config, captureOutput, onCaptured)
    }

    @Test
    fun testMaxOutputSizeOk() {
        val s = getStream(maxOutputSize = 6)
        s.write("kotlin".toByteArray())
    }

    @Test
    fun testMaxOutputSizeError() {
        val s = getStream(maxOutputSize = 3)
        s.write("java".toByteArray())
        assertArrayEquals("jav".toByteArray(), s.contents)
    }

    @Test
    fun testOutputCapturingFlag() {
        val contents = "abc".toByteArray()

        val s1 = getStream(captureOutput = false)
        s1.write(contents)
        assertEquals(0, s1.contents.size)

        val s2 = getStream(captureOutput = true)
        s2.write(contents)
        assertArrayEquals(contents, s2.contents)
    }

    @Test
    fun testMaxBufferSize() {
        val contents = "0123456789\nfortran".toByteArray()
        val expected = arrayOf("012", "345", "678", "9\n", "for", "tra", "n")

        val i = AtomicInteger()
        val s = getStream(maxBufferSize = 3) {
            assertEquals(expected[i.getAndIncrement()], it)
        }

        s.write(contents)
        s.flush()

        assertEquals(expected.size, i.get())
    }

    @Test
    fun testNewlineBufferSize() {
        val contents = "12345\n12\n3451234567890".toByteArray()
        val expected = arrayOf("12345\n", "12\n", "345123456", "7890")

        val i = AtomicInteger()
        val s = getStream(maxBufferSize = 9, maxBufferNewlineSize = 6) {
            assertEquals(expected[i.getAndIncrement()], it)
        }

        s.write(contents)
        s.flush()

        assertEquals(expected.size, i.get())
    }

    @Test
    @Disabled("This test is flaky, enable after fix")
    fun testMaxBufferLifeTime() {
        val strings = arrayOf("11", "22", "33", "44", "55", "66")
        val expected = arrayOf("1122", "3344", "5566")

        val timeDelta = 2000L
        var i = 0
        val s = getStream(maxBufferLifeTimeMs = 2 * timeDelta) {
            synchronized(this) {
                assertEquals(expected[i++], it)
            }
        }

        Thread.sleep(timeDelta / 2)
        strings.forEach {
            s.write(it.toByteArray())
            Thread.sleep(timeDelta)
        }

        s.flush()
        s.close()

        assertEquals(expected.size, i)
    }
}
