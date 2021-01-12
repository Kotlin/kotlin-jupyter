package org.jetbrains.kotlinx.jupyter.test

import org.jetbrains.kotlinx.jupyter.EvalResult
import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.repl.impl.getSimpleCompiler
import org.jetbrains.kotlinx.jupyter.test.repl.AbstractReplTest
import org.junit.jupiter.api.Test
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiTest : AbstractReplTest() {
    private val repl = ReplForJupyterImpl(libraryFactory, classpath)

    private fun jEval(jupyterId: Int, code: String): EvalResult {
        return repl.eval(code, jupyterId = jupyterId)
    }

    @Test
    fun testRepl() {
        jEval(1, "val x = 3")
        jEval(2, "x*2")
        jEval(
            3,
            """
            println(x*3)
            """.trimIndent()
        )
        val res1 = jEval(4, "notebook.cells[2]?.result")
        assertEquals(6, res1.resultValue)
    }

    @Test
    fun compilerVersion() {
        val jCompiler = getSimpleCompiler(
            ScriptCompilationConfiguration(),
            ScriptEvaluationConfiguration()
        )
        val version = jCompiler.version
        assertTrue(version.major >= 0)
    }
}
