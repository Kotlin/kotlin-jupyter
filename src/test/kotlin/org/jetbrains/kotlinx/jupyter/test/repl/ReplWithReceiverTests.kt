package org.jetbrains.kotlinx.jupyter.test.repl

import jupyter.kotlin.receivers.ConstReceiver
import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.test.classpath
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class ReplWithReceiverTests : AbstractReplTest() {
    @Test
    fun testReplWithReceiver() {
        val value = 5
        val cp = classpath + File(ConstReceiver::class.java.protectionDomain.codeSource.location.toURI().path)
        val repl = ReplForJupyterImpl(resolutionInfoProvider, cp, null, scriptReceivers = listOf(ConstReceiver(value)))
        val res = repl.eval("value")
        assertEquals(value, res.resultValue)
    }
}
