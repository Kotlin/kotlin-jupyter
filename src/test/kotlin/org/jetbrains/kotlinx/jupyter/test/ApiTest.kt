package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.collections.shouldBeOneOf
import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.generateHTMLVarsReport
import org.jetbrains.kotlinx.jupyter.repl.EvalResult
import org.jetbrains.kotlinx.jupyter.repl.impl.getSimpleCompiler
import org.jetbrains.kotlinx.jupyter.test.repl.AbstractSingleReplTest
import org.jetbrains.kotlinx.jupyter.varsTableStyleClass
import org.junit.jupiter.api.Test
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiTest : AbstractSingleReplTest() {
    override val repl = makeSimpleRepl()

    private fun jEval(jupyterId: Int, code: String): EvalResult {
        return eval(code, jupyterId = jupyterId)
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

    @Test
    fun `check jupyter client detection`() {
        repl.notebook.jupyterClientType shouldBeOneOf listOf(JupyterClientType.KERNEL_TESTS, JupyterClientType.UNKNOWN)
    }

    @Test
    fun testVarsReportFormat() {
        eval(
            """
            val x = 1
            val y = "abc"
            val z = 47
            """.trimIndent()
        )

        val varsUpdate = mutableMapOf(
            "x" to "1",
            "y" to "abc",
            "z" to "47"
        )
        val htmlText = generateHTMLVarsReport(repl.notebook.variablesState)
        assertEquals(
            """
            <style>
            table.$varsTableStyleClass, .$varsTableStyleClass th, .$varsTableStyleClass td {
              border: 1px solid black;
              border-collapse: collapse;
              text-align:center;
            }
            .$varsTableStyleClass th, .$varsTableStyleClass td {
              padding: 5px;
            }
            </style>
            <h2 style="text-align:center;">Variables State</h2>
            <table class="$varsTableStyleClass" style="width:80%;margin-left:auto;margin-right:auto;" align="center">
              <tr>
                <th>Variable</th>
                <th>Value</th>
              </tr>
            <tr>
                <td>x</td>
                <td><pre>1</pre></td>
            </tr><tr>
                <td>y</td>
                <td><pre>abc</pre></td>
            </tr><tr>
                <td>z</td>
                <td><pre>47</pre></td>
            </tr>
            </table>
            
            """.trimIndent(),
            htmlText
        )
        assertEquals(varsUpdate, repl.notebook.variablesState.mapToStringValues())
    }
}
