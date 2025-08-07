package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.kotlinx.jupyter.api.ExactRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.MimeTypes
import org.jetbrains.kotlinx.jupyter.api.ResultHandlerCodeExecution
import org.jetbrains.kotlinx.jupyter.api.SubtypeRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResource
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourceFallbacksBundle
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourceLocation
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourcePathType
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourceType
import org.jetbrains.kotlinx.jupyter.api.libraries.libraryDefinition
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.test.repl.AbstractSingleReplTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SomeSingleton {
    companion object {
        var initialized: Boolean = false
    }
}

/**
 * Used for [EmbedReplTest.testSubtypeAndExactRenderer]
 */
@Suppress("unused")
class TestSum(
    val a: Int,
    val b: Int,
)

/**
 * Used for [EmbedReplTest.testSubtypeAndExactRenderer]
 */
@Suppress("unused")
class TestQuad(
    val c: Int,
)

/**
 * Used for [EmbedReplTest.testSubtypeAndExactRenderer]
 */
class TestFunList<T>(
    private val head: T,
    private val tail: TestFunList<T>?,
) {
    @Suppress("unused")
    fun render(): String =
        generateSequence(this) {
            it.tail
        }.joinToString(", ", "[", "]") {
            it.head.toString()
        }
}

/**
 * Used for [EmbedReplTest.testSubtypeAndExactRenderer]
 */
@Suppress("unused")
val testLibraryDefinition1 =
    libraryDefinition {
        it.renderers =
            listOf(
                SubtypeRendererTypeHandler(
                    TestSum::class,
                    ResultHandlerCodeExecution("\$it.a + \$it.b"),
                ),
                ExactRendererTypeHandler(
                    TestFunList::class.qualifiedName!!,
                    ResultHandlerCodeExecution("\$it.render()"),
                ),
                ExactRendererTypeHandler(
                    TestQuad::class.qualifiedName!!,
                    ResultHandlerCodeExecution("\$it.c * `\$it`.c"),
                ),
            )
    }

/**
 * Used for [EmbeddedTestWithHackedDisplayHandler.testJsResources]
 */
@Suppress("unused")
val testLibraryDefinition2 =
    libraryDefinition {
        it.resources =
            listOf(
                LibraryResource(
                    listOf(
                        ResourceFallbacksBundle(
                            ResourceLocation(
                                "https://cdn.plot.ly/plotly-latest.min.js",
                                ResourcePathType.URL,
                            ),
                        ),
                        ResourceFallbacksBundle(
                            ResourceLocation(
                                "src/test/testData/js-lib.js",
                                ResourcePathType.LOCAL_PATH,
                            ),
                        ),
                    ),
                    ResourceType.JS,
                    "testLib2",
                ),
            )
    }

class EmbedReplTest : AbstractSingleReplTest() {
    override val repl = makeEmbeddedRepl()

    @Test
    fun testSharedStaticVariables() {
        var res = eval("org.jetbrains.kotlinx.jupyter.test.SomeSingleton.initialized")
        assertEquals(false, res.renderedValue)

        SomeSingleton.initialized = true

        res = eval("org.jetbrains.kotlinx.jupyter.test.SomeSingleton.initialized")
        assertEquals(true, res.renderedValue)
    }

    // Test for https://youtrack.jetbrains.com/issue/KTNB-978/K2-Repl-Some-custom-class-names-crash-the-compiler
    @Test
    fun testCustomClasses() {
        eval("class Point(val x: Int, val y: Int)")
        eval("val p = Point(1,1)")
        val res = eval("p.x")
        assertEquals(1, res.renderedValue)
    }

    @Test
    fun testSubtypeAndExactRenderer() {
        repl.eval {
            addLibrary(testLibraryDefinition1)
        }
        val result1 = eval("org.jetbrains.kotlinx.jupyter.test.TestSum(5, 8)")
        when (repl.compilerMode) {
            K1 -> {
                result1.renderedValue shouldBe 13
                val result2 =
                    eval(
                        """
                        import org.jetbrains.kotlinx.jupyter.test.TestFunList
                        TestFunList(12, TestFunList(13, TestFunList(14, null)))
                        """.trimIndent(),
                    )
                result2.renderedValue shouldBe "[12, 13, 14]"

                val result3 =
                    eval(
                        """
                        import org.jetbrains.kotlinx.jupyter.test.TestQuad
                        TestQuad(15)
                        """.trimIndent(),
                    )
                result3.renderedValue shouldBe 225
            }
            K2 -> {
                // Type renderes do not work yet due to:
                // https://youtrack.jetbrains.com/issue/KT-76172/K2-Repl-Snippet-classes-do-not-store-result-values
                result1.renderedValue.shouldBeNull()
            }
        }
    }

    @Test
    fun testManualDependencies() {
        eval(
            """
            USE {
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation("io.github.config4k:config4k:0.4.2")
                }
            }
            """.trimIndent(),
        )

        val res = repl.completeBlocking("import io.github.", 17)
        res.shouldBeInstanceOf<CompletionResult.Success>()
        res.sortedMatches().contains("config4k")
    }

    @Test
    fun testLibraryDescriptors() {
        val result =
            eval(
                """
                %use serialization
                @Serializable class Test(val x: Int)
                """.trimIndent(),
            )
        when (repl.compilerMode) {
            K1 -> {
                result.shouldBeInstanceOf<org.jetbrains.kotlinx.jupyter.repl.result.EvalResultEx.Success>()
            }
            K2 -> {
                // Fails because of https://youtrack.jetbrains.com/issue/KT-75672/K2-Repl-Serialization-plugin-crashes-compiler-backend
                result.shouldBeInstanceOf<org.jetbrains.kotlinx.jupyter.repl.result.EvalResultEx.Error>()
            }
        }
    }
}

class EmbeddedTestWithHackedDisplayHandler : AbstractSingleReplTest() {
    private val displayHandler = TestDisplayHandler()
    override val repl = makeEmbeddedRepl(displayHandler = displayHandler)

    @Test
    fun testJsResources() {
        val res =
            eval(
                "USE(org.jetbrains.kotlinx.jupyter.test.testLibraryDefinition2)",
            )
        assertTrue(res.renderedValue is Unit)
        assertEquals(1, displayHandler.list.size)
        val typedResult = displayHandler.list[0] as MimeTypedResult
        val content = typedResult[MimeTypes.HTML]!!
        assertTrue(content.contains("""id="kotlin_out_0""""))
        assertTrue(content.contains("""function test_fun(x)"""))
    }
}
