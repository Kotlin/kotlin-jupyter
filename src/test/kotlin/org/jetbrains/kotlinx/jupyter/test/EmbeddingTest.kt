package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
                    ResultHandlerCodeExecution($$"$it.a + $it.b"),
                ),
                ExactRendererTypeHandler(
                    TestFunList::class.qualifiedName!!,
                    ResultHandlerCodeExecution($$"$it.render()"),
                ),
                ExactRendererTypeHandler(
                    TestQuad::class.qualifiedName!!,
                    ResultHandlerCodeExecution($$"$it.c * `$it`.c"),
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
        res.renderedValue shouldBe false

        SomeSingleton.initialized = true

        res = eval("org.jetbrains.kotlinx.jupyter.test.SomeSingleton.initialized")
        res.renderedValue shouldBe true
    }

    // Test for KTNB-978
    @Test
    fun testCustomClasses() {
        eval("class Point(val x: Int, val y: Int)")
        eval("val p = Point(1,1)")
        val res = eval("p.x")
        res.renderedValue shouldBe 1
    }

    @Test
    fun testSubtypeAndExactRenderer() {
        repl.eval {
            addLibrary(testLibraryDefinition1)
        }
        val result1 = eval("org.jetbrains.kotlinx.jupyter.test.TestSum(5, 8)")
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
        result.shouldBeInstanceOf<org.jetbrains.kotlinx.jupyter.repl.result.EvalResultEx.Success>()
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
        (res.renderedValue is Unit).shouldBeTrue()
        displayHandler.list.size shouldBe 1
        val typedResult = displayHandler.list[0] as MimeTypedResult
        val content = typedResult[MimeTypes.HTML]!!
        content shouldContain """id="kotlin_out_0""""
        content shouldContain """function test_fun(x)"""
    }
}
