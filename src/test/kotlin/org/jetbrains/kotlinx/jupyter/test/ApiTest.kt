package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeOneOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import jupyter.kotlin.VARIABLES_TABLE_STYLE_CLASS
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResultEx
import org.jetbrains.kotlinx.jupyter.api.session.JupyterSessionInfo
import org.jetbrains.kotlinx.jupyter.repl.impl.getSimpleCompiler
import org.jetbrains.kotlinx.jupyter.repl.result.EvalResultEx
import org.jetbrains.kotlinx.jupyter.test.repl.AbstractSingleReplTest
import org.junit.jupiter.api.Test
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiTest : AbstractSingleReplTest() {
    override val repl = makeSimpleRepl()

    private fun jEval(
        jupyterId: Int,
        code: String,
    ): EvalResultEx = eval(code, jupyterId)

    @Test
    fun testRepl() {
        jEval(1, "val x = 3")
        jEval(2, "x*2")
        jEval(
            3,
            """
            println(x*3)
            """.trimIndent(),
        )
        val res1 = jEval(4, "notebook.getCell(2)?.result")
        assertEquals(6, res1.rawValue)
        val res2 = jEval(5, "notebook.getResult(2)")
        assertEquals(6, res2.rawValue)
    }

    @Test
    fun compilerVersion() {
        val jCompiler =
            getSimpleCompiler(
                ScriptCompilationConfiguration(),
                ScriptEvaluationConfiguration(),
            )
        val version = jCompiler.version
        assertTrue(version.major >= 0)
    }

    @Test
    fun `check jupyter client detection`() {
        repl.notebook.jupyterClientType shouldBeOneOf listOf(JupyterClientType.KERNEL_TESTS, JupyterClientType.UNKNOWN)
    }

    @Test
    fun `check that active kernel session is detected`() {
        JupyterSessionInfo.isRunWithKernel().shouldBeTrue()
    }

    @Test
    fun testVarsReportFormat() {
        eval(
            """
            val x = 1
            val y = "abc"
            val z = 47
            """.trimIndent(),
        )

        val varsUpdate =
            mutableMapOf(
                "x" to "1",
                "y" to "abc",
                "z" to "47",
            )
        val htmlText = eval("notebook.variablesReportAsHTML").renderedValue
        htmlText.shouldBeTypeOf<MimeTypedResult>()
        assertEquals(
            """
            <style>
            table.$VARIABLES_TABLE_STYLE_CLASS, .$VARIABLES_TABLE_STYLE_CLASS th, .$VARIABLES_TABLE_STYLE_CLASS td {
              border: 1px solid black;
              border-collapse: collapse;
              text-align:center;
            }
            .$VARIABLES_TABLE_STYLE_CLASS th, .$VARIABLES_TABLE_STYLE_CLASS td {
              padding: 5px;
            }
            </style>
            <h2 style="text-align:center;">Variables State</h2>
            <table class="$VARIABLES_TABLE_STYLE_CLASS" style="width:80%;margin-left:auto;margin-right:auto;" align="center">
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
            htmlText.values.first(),
        )
        assertEquals(varsUpdate, repl.notebook.variablesState.mapToStringValues())
    }

    @Test
    fun `check that JSON output has correct metadata`() {
        val res =
            eval(
                """
                val jsonStr = ""${'"'}
                {"a": [1], "b": {"inner1": "helloworld\n````"}}
                ""${'"'}

                JSON(jsonStr)
                """.trimIndent(),
            ).renderedValue
        res.shouldBeInstanceOf<MimeTypedResultEx>()
        val displayJson = res.toJson(overrideId = null)

        val format = Json { prettyPrint = true }
        format.encodeToString(displayJson) shouldBe
            """
            {
                "data": {
                    "application/json": {
                        "a": [
                            1
                        ],
                        "b": {
                            "inner1": "helloworld\n````"
                        }
                    },
                    "text/plain": "{\n    \"a\": [\n        1\n    ],\n    \"b\": {\n        \"inner1\": \"helloworld\\n````\"\n    }\n}",
                    "text/markdown": "`````json\n{\n    \"a\": [\n        1\n    ],\n    \"b\": {\n        \"inner1\": \"helloworld\\n````\"\n    }\n}\n`````"
                },
                "metadata": {
                    "application/json": {
                        "expanded": true
                    }
                }
            }
            """.trimIndent()
    }
}
