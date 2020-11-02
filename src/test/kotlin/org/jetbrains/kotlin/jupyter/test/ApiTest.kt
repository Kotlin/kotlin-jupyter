package org.jetbrains.kotlin.jupyter.test

import org.jetbrains.kotlin.jupyter.EvalResult
import org.jetbrains.kotlin.jupyter.ReplForJupyterImpl
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
    fun addLibrary() {
        repl.eval(
            """
            object customLib: LibraryDefinition {
                override val init: List<Execution>
                    get() = listOf(Execution{ it.scheduleExecution("val x = 42") })
            }
            notebook.host.addLibrary(customLib)
            """.trimIndent()
        )

        val res = repl.eval("x")
        assertEquals(42, res.resultValue)
    }
}
