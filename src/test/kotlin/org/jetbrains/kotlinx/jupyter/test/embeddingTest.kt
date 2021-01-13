package org.jetbrains.kotlinx.jupyter.test

import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.SubtypeRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.TypeHandlerCodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionImpl
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResource
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourceLocation
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourcePathType
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourceType
import org.jetbrains.kotlinx.jupyter.execute
import org.jetbrains.kotlinx.jupyter.test.repl.AbstractReplTest
import org.junit.jupiter.api.Test
import java.io.File
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
val testLibraryDefinition1 = LibraryDefinitionImpl(
    renderers = listOf(
        SubtypeRendererTypeHandler(
            TestSum::class,
            TypeHandlerCodeExecution("\$it.a + \$it.b")
        ),
        SubtypeRendererTypeHandler(
            TestFunList::class,
            TypeHandlerCodeExecution("\$it.render()")
        )
    )
)

/**
 * Used for [EmbedReplTest.testJsResources]
 */
@Suppress("unused")
val testLibraryDefinition2 = LibraryDefinitionImpl(
    resources = listOf(
        LibraryResource(
            listOf(
                ResourceLocation(
                    "https://cdn.plot.ly/plotly-latest.min.js",
                    ResourcePathType.URL
                ),
                ResourceLocation(
                    "src/test/testData/js-lib.js",
                    ResourcePathType.LOCAL_PATH
                )
            ),
            ResourceType.JS,
            "testLib2"
        )
    )
)

class EmbedReplTest : AbstractReplTest() {
    private val repl = run {
        val embeddedClasspath: List<File> = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
        ReplForJupyterImpl(libraryFactory, embeddedClasspath, embedded = true)
    }

    @Test
    fun testSharedStaticVariables() {
        var res = repl.eval("org.jetbrains.kotlinx.jupyter.test.SomeSingleton.initialized")
        assertEquals(false, res.resultValue)

        SomeSingleton.initialized = true

        res = repl.eval("org.jetbrains.kotlinx.jupyter.test.SomeSingleton.initialized")
        assertEquals(true, res.resultValue)
    }

    @Test
    fun testCustomClasses() {
        repl.eval("class Point(val x: Int, val y: Int)")
        repl.eval("val p = Point(1,1)")

        val res = repl.eval("p.x")
        assertEquals(1, res.resultValue)
    }

    @Test
    fun testSubtypeRenderer() {
        repl.execute {
            addLibrary(testLibraryDefinition1)
        }
        val result1 = repl.eval("org.jetbrains.kotlinx.jupyter.test.TestSum(5, 8)")
        assertEquals(13, result1.resultValue)
        val result2 = repl.eval(
            """
            import org.jetbrains.kotlinx.jupyter.test.TestFunList
            TestFunList(12, TestFunList(13, TestFunList(14, null)))
            """.trimIndent()
        )
        assertEquals("[12, 13, 14]", result2.resultValue)
    }

    @Test
    fun testJsResources() {
        val displayHandler = TestDisplayHandler()
        val res = repl.eval(
            "USE(org.jetbrains.kotlinx.jupyter.test.testLibraryDefinition2)",
            displayHandler
        )
        assertTrue(res.resultValue is Unit)
        assertEquals(1, displayHandler.list.size)
        val typedResult = displayHandler.list[0] as MimeTypedResult
        val content = typedResult["text/html"]!!
        assertTrue(content.contains("""id="kotlin_out_0""""))
        assertTrue(content.contains("""function test_fun(x)"""))
    }
}
