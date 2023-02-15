package org.jetbrains.kotlinx.jupyter.test.repl

import org.jetbrains.kotlinx.jupyter.test.TestDisplayHandlerWithRendering
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class ReplWithStandardResolverAndRenderingTests : AbstractSingleReplTest() {
    private val displays = mutableListOf<Any>()
    override val repl = makeReplWithStandardResolver { notebook ->
        TestDisplayHandlerWithRendering(notebook, displays)
    }

    @Test
    fun testDataframeDisplay() {
        repl.notebook.beginEvalSession()
        eval("SessionOptions.resolveSources = true", 1, false)

        repl.notebook.beginEvalSession()
        eval(
            """
            %use dataframe(0.10.0-dev-1373)
            
            val name by column<String>()
            val height by column<Int>()
            
            dataFrameOf(name, height)(
                "Bill", 135,
                "Mark", 160
            )
            """.trimIndent(),
            2,
            true,
        )

        repl.notebook.beginEvalSession()
        eval(
            """DISPLAY((Out[2] as DataFrame<*>).filter { it.index() >= 0 && it.index() <= 10 }, "")""",
            3,
            false,
        )
    }
}
