package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.api.DeclarationKind
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
        eval("SessionOptions.resolveSources = true", 1, false)

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

        val declaredProperties = repl.notebook.currentCell!!.declarations
            .filter { it.kind == DeclarationKind.PROPERTY }
            .mapNotNull { it.name }

        declaredProperties shouldBe listOf("name", "height")

        eval(
            """DISPLAY((Out[2] as DataFrame<*>).filter { it.index() >= 0 && it.index() <= 10 }, "")""",
            3,
            false,
        )
    }
}
