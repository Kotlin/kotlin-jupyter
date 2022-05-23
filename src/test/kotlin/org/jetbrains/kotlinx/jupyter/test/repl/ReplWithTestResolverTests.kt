package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.test.TestDisplayHandler
import org.jetbrains.kotlinx.jupyter.test.assertUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeout
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Execution(ExecutionMode.SAME_THREAD)
class ReplWithTestResolverTests : AbstractSingleReplTest() {
    override val repl = makeReplWithTestResolver()

    @Test
    fun testLetsPlot() {
        val code1 = "%use lets-plot"
        val code2 =
            """lets_plot(mapOf<String, Any>("cat" to listOf("a", "b")))"""
        val displays = mutableListOf<Any>()
        val displayHandler = TestDisplayHandler(displays)

        val res1 = eval(code1, displayHandler)
        assertEquals(1, displays.count())
        displays.clear()
        assertUnit(res1.resultValue)
        val res2 = eval(code2, displayHandler)
        assertEquals(0, displays.count())
        val mime = res2.resultValue as? MimeTypedResult
        assertNotNull(mime)
        assertEquals(1, mime.size)
        assertEquals("text/html", mime.entries.first().key)
        assertNotNull(res2.resultValue)
    }

    @Test
    fun testDataframe() {
        eval("SessionOptions.resolveSources = true")

        val res = eval(
            """
            %use dataframe
            
            val name by column<String>()
            val height by column<Int>()
            
            dataFrameOf(name, height)(
                "Bill", 135,
                "Mark", 160
            )
            """.trimIndent()
        )

        val value = res.resultValue
        assertTrue(value is MimeTypedResult)

        val html = value["text/html"]!!
        assertTrue(html.contains("Bill"))

        res.metadata.newSources.shouldHaveAtLeastSize(3)
    }

    @Test
    fun testSerialization() {
        val serialized = eval(
            """
            %use serialization
            
            @Serializable
            class C(val x: Int)
            
            Json.encodeToString(C(42))
            """.trimIndent()
        )

        assertEquals("""{"x":42}""", serialized.resultValue)
    }

    @Test
    fun testTwoLibrariesInUse() {
        val code = "%use lets-plot, krangl"
        val displays = mutableListOf<Any>()
        val displayHandler = TestDisplayHandler(displays)

        eval(code, displayHandler)
        assertEquals(1, displays.count())
    }

    @Test
    fun testKranglImportInfixFun() {
        eval("""%use krangl, lets-plot""")
        val res = eval(""" "a" to {it["a"]} """)
        assertNotNull(res.resultValue)
    }

    @Test
    fun testRuntimeDepsResolution() {
        val res = eval(
            """
            %use krangl(0.17)
            val df = DataFrame.readCSV("src/test/testData/resolve-with-runtime.csv")
            df.head().rows.first().let { it["name"].toString() + " " + it["surname"].toString() }
            """.trimIndent()
        )
        assertEquals("John Smith", res.resultValue)
    }

    @Test
    fun testNullableErasure() {
        val code1 = "val a: Int? = 3"
        eval(code1)
        val code2 = "a+2"
        val res = eval(code2).resultValue
        assertEquals(5, res)
    }

    @Test
    fun testKlaxonClasspathDoesntLeak() {
        val res = eval(
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
            """.trimIndent()
        )
        assertEquals(23, res.resultValue)
    }

    @Test
    fun testLibNewClasspath() {
        val res = eval(
            """
            %use lets-plot
            """.trimIndent()
        )

        with(res.metadata) {
            assertTrue(newClasspath.size >= 10)
            assertTrue(newImports.size >= 5)
            assertTrue("jetbrains.letsPlot.*" in newImports)
        }
    }

    @Test
    fun testLibraryCompletion() {
        completeOrFail("%u", 2).sortedMatches() shouldBe listOf("use", "useLatestDescriptors")
        completeOrFail("%use kot", 8).sortedMatches() shouldContainAll listOf("kotlin-dl", "kotlin-statistics")
        with(completeOrFail("%use dataframe(0.8.0-)", 21).sortedMatches()) {
            shouldHaveAtLeastSize(10)
            shouldContain("0.8.0-rc-1")
        }
        completeOrFail("%use lets-plot, data", 20).sortedMatches() shouldBe listOf("dataframe")
        with(completeOrFail("%use kotlin-dl(", 15).matches()) {
            last() shouldBe "0.1.1"

            // Pre-release version should appear after release version
            indexOf("0.3.0-alpha-1") shouldBeGreaterThan indexOf("0.3.0")
        }

        // Value should be cached, and all these requests should not take much time
        assertTimeout(Duration.ofSeconds(20)) {
            for (i in 1..10000) {
                completeOrFail("%use kmath(", 11).matches() shouldHaveAtLeastSize 5
            }
        }
    }

    @Test
    fun testLibraryCompletionWithParams() {
        completeOrFail("%use kotlin-dl()", 15).matches() shouldHaveAtLeastSize 5
        completeOrFail("%use kotlin-dl(v =)", 15).matches() shouldHaveSize 0
        completeOrFail("%use kotlin-dl(v =", 18).matches() shouldHaveAtLeastSize 5
        completeOrFail("%use kotlin-dl(a =", 18).matches() shouldHaveSize 0

        completeOrFail("%use lets-plot(api = ", 21).matches() shouldContain "3.1.0"
        completeOrFail("%use lets-plot(api = , js", 21).matches() shouldContain "3.1.0"
        completeOrFail("%use lets-plot(api = 3.1.0, lib = ", 34).matches() shouldContain "2.2.0"
    }
}
