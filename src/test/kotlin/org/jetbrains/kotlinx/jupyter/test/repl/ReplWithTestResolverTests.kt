package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.kotlinx.jupyter.api.JSON
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.MimeTypes
import org.jetbrains.kotlinx.jupyter.repl.result.EvalResultEx
import org.jetbrains.kotlinx.jupyter.test.TestDisplayHandler
import org.jetbrains.kotlinx.jupyter.test.displayValue
import org.jetbrains.kotlinx.jupyter.test.rawValue
import org.jetbrains.kotlinx.jupyter.test.renderedValue
import org.jetbrains.kotlinx.jupyter.test.shouldBeUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeout
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Execution(ExecutionMode.SAME_THREAD)
class ReplWithTestResolverTests : AbstractSingleReplTest() {
    private val displays = mutableListOf<Any>()
    private val displayHandler = TestDisplayHandler(displays)
    override val repl = makeReplWithTestResolver(displayHandler)

    @Test
    fun testLetsPlot() {
        val code1 = "%use lets-plot"
        val code2 =
            """
            val data = mapOf<String, Any>(
                "City" to listOf("Berlin", "Krakow", "Amsterdam"),
                "Temperature" to listOf(10, -2, 13)
            )
            ggplot(data) { x = "City"; y = "Temperature" } +
            ggsize(700, 500) +
            geomBoxplot { fill = "City" } +
            // Customizes colors
            scaleFillManual(values = listOf("light_yellow", "light_magenta", "light_green"))
            """.trimIndent()

        val res1 = evalSuccess(code1)
        displays.count() shouldBe 2
        displays.clear()
        res1.renderedValue.shouldBeUnit()
        evalSuccess(code2)
        displays.count() shouldBe 1
        val mime = displays[0] as? MimeTypedResult
        mime.shouldNotBeNull()
        mime.size shouldBe 1
        mime.entries.first().key shouldBe MimeTypes.HTML
    }

    @Test
    fun testDataframe() {
        eval("SessionOptions.resolveSources = true")

        val res =
            eval(
                """
                %use dataframe
                
                val name by column<String>()
                val height by column<Int>()
                
                dataFrameOf(name, height)(
                    "Bill", 135,
                    "Mark", 160
                )
                """.trimIndent(),
            )

        val value = res.renderedValue
        assertTrue(value is MimeTypedResult)

        val html = value[MimeTypes.HTML]!!
        assertTrue(html.contains("Bill"))

        res.metadata.newSources.shouldHaveAtLeastSize(3)
    }

    @Test
    fun testSerialization() {
        val serialized =
            eval(
                """
                %use serialization
                
                @Serializable
                class C(val x: Int)
                
                Json.encodeToString(C(42))
                """.trimIndent(),
            )

        val expectedJson = """{"x":42}"""
        when (repl.compilerMode) {
            K1 -> {
                serialized.rawValue shouldBe expectedJson
                serialized.renderedValue shouldBe JSON(expectedJson)
            }
            K2 -> {
                serialized.shouldBeInstanceOf<EvalResultEx.Error>()
            }
        }
    }

    @Test
    fun testNullableErasure() {
        eval(
            """
            val a: Int? = 3
            val b: String? = a.toString()
            """.trimIndent(),
        )
        val code2 = "a + 2 + b.toInt()"
        val res = eval(code2)
        res.renderedValue shouldBe 8
    }

    @Test
    fun testKlaxonClasspathDoesntLeak() {
        val res =
            eval(
                """
                @file:DependsOn("src/test/testData/klaxon-2.1.8.jar")
                import com.beust.klaxon.*
                
                class Person (val name: String, var age: Int = 23)
                val klaxon = Klaxon()
                val parseRes = klaxon.parse<Person>(""${'"'}
                    {
                      "name": "John Smith"
                    }
                    ""${'"'})
                parseRes?.age
                """.trimIndent(),
            )
        assertEquals(23, res.renderedValue)
    }

    @Test
    fun testLibNewClasspath() {
        val res =
            eval(
                """
                %use lets-plot
                """.trimIndent(),
            )

        with(res.metadata) {
            assertTrue(newClasspath.size >= 10)
            assertTrue(newImports.size >= 5)
            assertTrue("org.jetbrains.letsPlot.*" in newImports)
        }
    }

    @Test
    fun testLibraryCompletion() {
        complete("%u|").sortedMatches() shouldBe listOf("use", "useLatestDescriptors")
        complete("%use kot|").sortedMatches() shouldContainAll listOf("kotlin-dl")
        with(complete("%use dataframe(0.8.0-|)").sortedMatches()) {
            shouldHaveAtLeastSize(10)
            shouldContain("0.8.0-rc-1")
        }
        complete("%use lets-plot, data|").sortedMatches() shouldBe listOf("dataframe")
        with(complete("%use kotlin-dl(|").matches()) {
            last() shouldBe "0.1.1"

            // Pre-release version should appear after release version
            indexOf("0.3.0-alpha-1") shouldBeGreaterThan indexOf("0.3.0")
        }

        // Value should be cached, and all these requests should not take much time
        assertTimeout(Duration.ofSeconds(20)) {
            repeat(10000) {
                complete("%use kmath(|").matches() shouldHaveAtLeastSize 5
            }
        }
    }

    @Test
    fun testLibraryCompletionWithParams() {
        complete("%use kotlin-dl(|)").matches() shouldHaveAtLeastSize 5
        complete("%use kotlin-dl(|v =)").matches() shouldBe listOf("v")
        complete("%use kotlin-dl(v =|").matches().apply {
            shouldHaveAtLeastSize(5)
            shouldNotContain("v")
        }
        complete("%use kotlin-dl(a =|").matches() shouldHaveSize 0

        complete("%use lets-plot(v = |").matches() shouldContain "4.9.2"
        complete("%use lets-plot(v = |, js").matches() shouldContain "4.9.2"
    }

    @Test
    fun testTextRenderersOnRealData() {
        val result =
            eval(
                """
                notebook.textRenderersProcessor.registerDefaultRenderers()
                val text = java.io.File("src/test/testData/textRenderers/textData.txt").readText()
                "<!---FUN (.+)-->".toRegex().findAll(text).toList()
                """.trimIndent(),
            )

        val html = (result.displayValue as MimeTypedResult)["text/plain"]!!
        html shouldStartWith "ArrayList[MatcherMatchResult("
    }
}
