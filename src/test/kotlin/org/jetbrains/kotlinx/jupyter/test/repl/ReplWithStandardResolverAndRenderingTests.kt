package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.api.DeclarationKind
import org.jetbrains.kotlinx.jupyter.api.createRenderer
import org.jetbrains.kotlinx.jupyter.api.libraries.createLibrary
import org.jetbrains.kotlinx.jupyter.test.TestDisplayHandlerWithRendering
import org.jetbrains.kotlinx.jupyter.test.evalEx
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

    @Test
    fun `display inside renderer works as expected`() {
        repl.eval {
            addLibrary(
                createLibrary(repl.notebook) {
                    addRenderer(
                        createRenderer(
                            { it.value is Int },
                            { host, field -> host.display("${field.value}: hi from host.display", null) },
                        ),
                    )
                },
            )
        }

        val result = repl.evalEx("42")
        result.renderedValue shouldBe Unit
        result.rawValue shouldBe 42

        displays.size shouldBe 1
        displays.single() shouldBe "42: hi from host.display"
    }
}
