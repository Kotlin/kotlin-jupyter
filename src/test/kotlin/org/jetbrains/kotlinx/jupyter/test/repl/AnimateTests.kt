package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.outputs.DisplayHandler
import org.jetbrains.kotlinx.jupyter.test.TestDisplayHandlerWithRendering
import org.jetbrains.kotlinx.jupyter.test.shouldBeText
import org.junit.jupiter.api.Test

class AnimateTests : AbstractSingleReplTest() {
    private val displays get() = repl.notebook.displays.getAll()

    private var displaysCount = 0
    private var updatesCount = 0

    private val myHandler =
        CompositeDisplayHandler().apply {
            addHandler(
                object : DisplayHandler {
                    override fun handleDisplay(
                        value: Any,
                        host: ExecutionHost,
                        id: String?,
                    ) {
                        ++displaysCount
                    }

                    override fun handleUpdate(
                        value: Any,
                        host: ExecutionHost,
                        id: String?,
                    ) {
                        ++updatesCount
                    }
                },
            )
        }

    override val repl =
        makeReplWithStandardResolver { notebook ->
            object : TestDisplayHandlerWithRendering(notebook) {
                override fun handleDisplay(
                    value: Any,
                    host: ExecutionHost,
                    id: String?,
                ) {
                    super.handleDisplay(value, host, id)
                    myHandler.handleDisplay(value, host, id)
                }

                override fun handleUpdate(
                    value: Any,
                    host: ExecutionHost,
                    id: String?,
                ) {
                    super.handleUpdate(value, host, id)
                    myHandler.handleUpdate(value, host, id)
                }
            }
        }

    @Test
    fun `animate works as expected`() {
        myHandler.addHandler(
            UpdateAsserter { value, _ -> value shouldBe (updatesCount + 1) },
        )

        eval(
            """
            ANIMATE(10.milliseconds, generateSequence(1) { it + 1 }.take(300))
            """.trimIndent(),
        )

        displaysCount shouldBe 1
        updatesCount shouldBe 299

        val displayResult = displays.single()

        displayResult.shouldBeText().toInt() shouldBe 300
    }

    @Test
    fun `animate in Notebook API works as expected`() {
        myHandler.addHandler(
            UpdateAsserter { value, _ -> value shouldBe (updatesCount + 1) },
        )

        eval(
            """
            notebook.animate(10.milliseconds, generateSequence(1) { it + 1 }.take(300))
            """.trimIndent(),
        )

        displaysCount shouldBe 1
        updatesCount shouldBe 299

        val displayResult = displays.single()

        displayResult.shouldBeText().toInt() shouldBe 300
    }

    private class UpdateAsserter(
        val asserter: (value: Any, id: String?) -> Unit,
    ) : DisplayHandlerBase() {
        override fun handleUpdate(
            value: Any,
            host: ExecutionHost,
            id: String?,
        ) {
            asserter(value, id)
        }
    }

    private open class DisplayHandlerBase : DisplayHandler {
        override fun handleDisplay(
            value: Any,
            host: ExecutionHost,
            id: String?,
        ) {
        }

        override fun handleUpdate(
            value: Any,
            host: ExecutionHost,
            id: String?,
        ) {
        }
    }

    private class CompositeDisplayHandler : DisplayHandler {
        private val handlers = mutableListOf<DisplayHandler>()

        override fun handleDisplay(
            value: Any,
            host: ExecutionHost,
            id: String?,
        ) {
            handlers.forEach { it.handleDisplay(value, host, id) }
        }

        override fun handleUpdate(
            value: Any,
            host: ExecutionHost,
            id: String?,
        ) {
            handlers.forEach { it.handleUpdate(value, host, id) }
        }

        fun addHandler(handler: DisplayHandler) {
            handlers.add(handler)
        }
    }
}
