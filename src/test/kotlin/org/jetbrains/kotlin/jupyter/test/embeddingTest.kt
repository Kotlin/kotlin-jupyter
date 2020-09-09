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
    fun testRepl() {
        val embeddedClasspath: List<File> = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
        val repl = ReplForJupyterImpl(libraryFactory, embeddedClasspath, embedded=true)

        var res = repl.eval("org.jetbrains.kotlin.jupyter.test.SomeSingleton.initialized")
        assertEquals(false, res.resultValue)

        SomeSingleton.initialized = true

        res = repl.eval("org.jetbrains.kotlin.jupyter.test.SomeSingleton.initialized")
        assertEquals(true, res.resultValue)

    }
}
