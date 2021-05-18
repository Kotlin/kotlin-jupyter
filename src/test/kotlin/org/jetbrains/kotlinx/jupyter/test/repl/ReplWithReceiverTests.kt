package org.jetbrains.kotlinx.jupyter.test.repl

import jupyter.kotlin.receivers.ConstReceiver
import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.test.classpath
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ReplWithReceiverTests : AbstractReplTest() {
    @Test
    fun testReplWithReceiver() {
        val value = 5
        val cp = classpath + File(ConstReceiver::class.java.protectionDomain.codeSource.location.toURI().path)
        val repl = ReplForJupyterImpl(resolutionInfoProvider, cp, null, implicitReceivers = listOf(ConstReceiver(value)))
        val res = repl.eval("value")
        assertEquals(value, res.resultValue)
    }

    @Test
    fun testReplWithAdHocReceivers() {
        val repl = makeSimpleRepl()
        repl.eval(
            """
            class A(val x: Int)
            object B { val y = 42 }
            """.trimIndent()
        )

        repl.eval(
            """
            HOST.withReceiver(A(50))
            HOST.withReceiver(B)
            """.trimIndent()
        )
        val resX = repl.eval("x").resultValue
        assertEquals(50, resX)

        repl.eval("HOST.removeReceiver<A>()")
        assertFails { repl.eval("x") }

        val resY = repl.eval("y").resultValue
        assertEquals(42, resY)
    }
}
