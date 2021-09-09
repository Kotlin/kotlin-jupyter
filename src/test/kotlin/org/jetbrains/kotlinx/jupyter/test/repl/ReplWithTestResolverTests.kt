package org.jetbrains.kotlinx.jupyter.test.repl

import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.test.TestDisplayHandler
import org.jetbrains.kotlinx.jupyter.test.assertUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
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
        val res = eval(
            """
            %use dataframe
            
            val name by column<String>()
            val height by column<Int>()
            
            dataFrameOf(name, height)(
                "Bill", 135,
                "Mark", 160
            ).typed<Unit>()
            """.trimIndent()
        )

        val value = res.resultValue
        assertTrue(value is MimeTypedResult)

        val html = value["text/html"]!!
        assertTrue(html.contains("Bill"))
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
}
