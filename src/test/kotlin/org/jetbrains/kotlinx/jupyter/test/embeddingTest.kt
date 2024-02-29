package org.jetbrains.kotlinx.jupyter.test

import org.jetbrains.kotlinx.jupyter.api.*
import org.jetbrains.kotlinx.jupyter.api.libraries.*
import org.jetbrains.kotlinx.jupyter.test.repl.AbstractSingleReplTest
import org.junit.jupiter.api.Test
import javax.swing.JFrame
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SomeSingleton {
    companion object {
        var initialized: Boolean = false
    }
}

/**
 * Used for [EmbedReplTest.testSubtypeRenderer]
 */
@Suppress("unused")
class TestSum(val a: Int, val b: Int)

/**
 * Used for [EmbedReplTest.testSubtypeRenderer]
 */
class TestFunList<T>(private val head: T, private val tail: TestFunList<T>?) {
    @Suppress("unused")
    fun render(): String {
        return generateSequence(this) {
            it.tail
        }.joinToString(", ", "[", "]") {
            it.head.toString()
        }
    }
}

/**
 * Used for [EmbedReplTest.testSubtypeRenderer]
 */
@Suppress("unused")
val testLibraryDefinition1 = libraryDefinition {
    it.renderers = listOf(
        SubtypeRendererTypeHandler(
            TestSum::class,
            ResultHandlerCodeExecution("\$it.a + \$it.b"),
        ),
        SubtypeRendererTypeHandler(
            TestFunList::class,
            ResultHandlerCodeExecution("\$it.render()"),
        ),
    )
}

/**
 * Used for [EmbeddedTestWithHackedDisplayHandler.testJsResources]
 */
@Suppress("unused")
val testLibraryDefinition2 = libraryDefinition {
    it.resources = listOf(
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

    @Test
    fun testCustomClasses() {
        eval("class Point(val x: Int, val y: Int)")
        eval("val p = Point(1,1)")

        val res = eval("p.x")
        assertEquals(1, res.renderedValue)
    }

    @Test
    fun testSubtypeRenderer() {
        repl.eval {
            addLibrary(testLibraryDefinition1)
        }
        val result1 = eval("org.jetbrains.kotlinx.jupyter.test.TestSum(5, 8)")
        assertEquals(13, result1.renderedValue)
        val result2 = eval(
            """
            import org.jetbrains.kotlinx.jupyter.test.TestFunList
            TestFunList(12, TestFunList(13, TestFunList(14, null)))
            """.trimIndent(),
        )
        assertEquals("[12, 13, 14]", result2.renderedValue)
    }

    @Test
    fun testInMemoryCellValues() {
        val code = """
            import javax.swing.JFrame
            val frame = JFrame("panel")
            frame.setSize(300, 300)
            frame
        """.trimIndent()
        val result  = eval(code)
        assertTrue(result.renderedValue is InMemoryMimeTypedResult)
        assertTrue(result.displayValue is InMemoryMimeTypedResult)
        val display = result.displayValue as InMemoryMimeTypedResult
        assertTrue(display.inMemoryOutput.result is JFrame)
        assertEquals(InMemoryMimeTypes.SWING, display.inMemoryOutput.mimeType)
    }
}

class EmbeddedTestWithHackedDisplayHandler : AbstractSingleReplTest() {
    private val displayHandler = TestDisplayHandler()
    override val repl = makeEmbeddedRepl(displayHandler = displayHandler)

    @Test
    fun testJsResources() {
        val res = eval(
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
