package org.jetbrains.kotlinx.jupyter.test

import org.jetbrains.kotlinx.jupyter.EvalResult
import org.jetbrains.kotlinx.jupyter.repl.impl.getSimpleCompiler
import org.jetbrains.kotlinx.jupyter.test.repl.AbstractSingleReplTest
import org.junit.jupiter.api.Test
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiTest : AbstractSingleReplTest() {
    override val repl = makeSimpleRepl()

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
        val res1 = jEval(4, "notebook.getCell(2)?.result")
        assertEquals(6, res1.resultValue)
        val res2 = jEval(5, "notebook.getResult(2)")
        assertEquals(6, res2.resultValue)
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
