package org.jetbrains.kotlin.jupyter.test

import org.jetbrains.kotlin.jupyter.ReplForJupyterImpl
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class SomeSingleton {
    companion object {
        var initialized: Boolean = false
    }
}

class EmbedReplTest : AbstractReplTest() {

    @Test
    fun testSharedStaticVariables() {
        val embeddedClasspath: List<File> = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
        val repl = ReplForJupyterImpl(libraryFactory, embeddedClasspath, embedded = true)

        var res = repl.eval("org.jetbrains.kotlin.jupyter.test.SomeSingleton.initialized")
        assertEquals(false, res.resultValue)

        SomeSingleton.initialized = true

        res = repl.eval("org.jetbrains.kotlin.jupyter.test.SomeSingleton.initialized")
        assertEquals(true, res.resultValue)
    }

    @Test
    fun testCustomClasses() {
        val embeddedClasspath: List<File> = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
        val repl = ReplForJupyterImpl(libraryFactory, embeddedClasspath, embedded = true)

        repl.eval("class Point(val x: Int, val y: Int)")

        repl.eval("val p = Point(1,1)")

        val res = repl.eval("p.x")
        assertEquals(1, res.resultValue)
    }
}
