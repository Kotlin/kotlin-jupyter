package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.MimeTypes
import org.jetbrains.kotlinx.jupyter.api.ResultFieldUpdateHandler
import org.jetbrains.kotlinx.jupyter.api.createRenderer
import org.jetbrains.kotlinx.jupyter.test.evalRaw
import org.jetbrains.kotlinx.jupyter.test.evalRendered
import org.jetbrains.kotlinx.jupyter.test.library
import org.junit.jupiter.api.Test

class TypeConverterTests : AbstractReplTest() {
    @Test
    fun `code should be generated and executed for correct variables`() {
        with(
            makeReplEnablingSingleLibrary(
                library {
                    onVariable<Any> { _, prop ->
                        execute("val gen_${prop.name} = 1")
                    }
                },
            ),
        ) {
            evalRaw(
                """
                val (a, b) = 1 to 's'
                val c = "42"
                35
                """.trimIndent(),
            )

            evalRaw(
                """
            gen_a + gen_b + gen_c
                """.trimIndent(),
            ) shouldBe 3
        }
    }

    @Test
    fun `code should be generated for particular result fields`() {
        var resultInvocationCounter = 0

        with(
            makeReplEnablingSingleLibrary(
                library {
                    addTypeConverter(
                        ResultFieldUpdateHandler(
                            updateCondition = { value, _ -> value == 35 },
                            updateAction = { _, _, _ -> ++resultInvocationCounter; null },
                        ),
                    )
                },
            ),
        ) {
            evalRaw(
                """
                val t = 35
                val b = t
                35
                """.trimIndent(),
            )

            resultInvocationCounter shouldBe 1
        }
    }

    @Test
    fun `type converter should be only applied once for each property (and not more than one at a time)`() {
        var counter1 = 0
        var counter2 = 0

        with(
            makeReplEnablingSingleLibrary(
                library {
                    onVariable<List<Int>> { _, _ ->
                        ++counter1
                    }
                    onVariable<List<Int>> { _, _ ->
                        ++counter2
                    }
                },
            ),
        ) {
            evalRaw("val ls = listOf(1, 2)")
            evalRaw("ls") // result field shouldn't be considered with this type of converters
            evalRaw("val ls2 = ls")

            counter1 shouldBe 0
            counter2 shouldBe 2 // type converted that was added later has higher priority
        }
    }

    @Test
    fun `result field created by type converter should be rendered correctly`() {
        with(
            makeReplEnablingSingleLibrary(
                library {
                    val wrapperClassName = "ResultWrapper"

                    // Define the class that can wrap integers
                    onLoaded { execute("class $wrapperClassName(val x: Int)") }

                    // Add type converter only for results of Int type, and only for even numbers
                    // This type converter wraps these numbers to the wrapper we defined above
                    addTypeConverter(
                        ResultFieldUpdateHandler(
                            updateCondition = { value, _ -> (value as? Int)?.let { value % 2 == 0 } ?: false },
                            updateAction = { host, _, field -> host.execute("$wrapperClassName(${field.name})").name },
                        ),
                    )

                    // Add renderer for the instances of the wrapper class that renders them to HTML
                    addRenderer(
                        createRenderer(
                            renderCondition = { it.value?.let { v -> v::class.simpleName == wrapperClassName } ?: false },
                            renderAction = { host, fieldValue ->
                                host.execute("(${fieldValue.name} as $wrapperClassName).x.let {v -> HTML(\"<b>\${v * 2}</b>\")}").value
                            },
                        ),
                    )
                },
            ),
        ) {
            evalRendered(
                """
                val a = 22
                32
                """.trimIndent(),
            ).let { renderedResult ->
                renderedResult.shouldBeTypeOf<MimeTypedResult>()
                renderedResult[MimeTypes.HTML] shouldBe "<b>64</b>"
            }

            evalRendered(
                """
                val a = 22
                33
                """.trimIndent(),
            ).let { renderedResult ->
                renderedResult.shouldBeTypeOf<Int>()
                renderedResult shouldBe 33
            }
        }
    }
}
