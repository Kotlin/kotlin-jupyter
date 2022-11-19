package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.api.Renderable
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.repl.creating.createRepl
import org.jetbrains.kotlinx.jupyter.test.classpath
import org.jetbrains.kotlinx.jupyter.test.library
import org.jetbrains.kotlinx.jupyter.test.testRepositories
import org.jetbrains.kotlinx.jupyter.test.toLibraries
import org.jetbrains.kotlinx.jupyter.util.EMPTY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IntegrationApiTests {
    private fun makeRepl(libraryResolver: LibraryResolver): ReplForJupyter {
        return createRepl(EmptyResolutionInfoProvider, classpath, null, testRepositories, libraryResolver)
    }
    private fun makeRepl(vararg libs: Pair<String, LibraryDefinition>): ReplForJupyter {
        return makeRepl(libs.toList().toLibraries())
    }

    @Test
    fun `field handling`() {
        val lib = "mylib" to library {
            val generated = mutableSetOf<Int>()
            updateVariable<List<Int>> { list, property ->

                val size = list.size
                val className = "TypedIntList$size"
                val propRef = if (property.returnType.isMarkedNullable) property.name + "!!" else property.name
                val converter = "$className($propRef)"
                if (generated.contains(size)) {
                    execute(converter).name!!
                } else {
                    val properties = (list.indices).joinToString("\n") { "val value$it : Int get() = list[$it]" }

                    val classDeclaration = """
                    class $className(val list: List<Int>): List<Int> by list {
                        $properties                    
                    }
                    $converter
                    """.trimIndent()

                    generated.add(size)
                    execute(classDeclaration).name!!
                }
            }
        }

        val repl = makeRepl(lib)

        // create list 'l' of size 3
        val code1 =
            """
            %use mylib
            val l = listOf(1,2,3)
            """.trimIndent()
        repl.eval(code1)
        assertEquals(3, repl.eval("l.value2").resultValue)

        // create list 'q' of the same size 3
        repl.eval("val q = l.asReversed()")
        assertEquals(1, repl.eval("q.value2").resultValue)

        // check that 'l' and 'q' have the same types
        assertEquals(
            3,
            repl.eval(
                """var a = l
            a = q
            a.value0
        """.trimMargin()
            ).resultValue
        )

        // create a list of size 6
        repl.eval("val w = l + a")
        assertEquals(3, repl.eval("w.value3").resultValue)

        // check that 'value3' is not available for list 'l'
        assertThrows<ReplCompilerException> {
            repl.eval("l.value3")
        }

        repl.eval("val e: List<Int>? = w.take(5)")
        val res = repl.eval("e").resultValue

        assertEquals("TypedIntList5", res!!.javaClass.simpleName)
    }

    @Test
    fun `after cell execution`() {
        val lib = "mylib" to library {
            afterCellExecution { _, _ ->
                execute("2")
            }
        }
        val repl = makeRepl(lib).trackExecution()

        repl.execute("%use mylib\n1")

        assertEquals(2, repl.executedCodes.size)
        assertEquals(1, repl.results[0])
        assertEquals(2, repl.results[1])
    }

    @Test
    fun `renderable objects`() {
        val repl = makeRepl()
        repl.eval(
            """
            @file:DependsOn("src/test/testData/kotlin-jupyter-api-test-0.0.16.jar")
            """.trimIndent()
        )

        val res = repl.eval(
            """
            ses.visualizeColor("red")
            """.trimIndent()
        )

        val result = res.resultValue as Renderable
        val json = result.render(repl.notebook).toJson(Json.EMPTY, null)
        val jsonData = json["data"] as JsonObject
        val htmlString = jsonData["text/html"] as JsonPrimitive
        kotlin.test.assertEquals("""<span style="color:red">red</span>""", htmlString.content)
    }

    @Test
    fun `library options`() {
        val libs = listOf(
            "lib" to """
                {
                    "dependencies": [
                        "src/test/testData/kotlin-jupyter-api-test-0.0.18.jar"
                    ]
                }
            """.trimIndent()
        )
        val repl = makeRepl(libs.toLibraries())
        repl.eval("%use lib(a = 42, b=foo)")

        val res = repl.eval("integrationOptions")

        val result = res.resultValue
        result.shouldBeInstanceOf<Map<String, String>>()

        result["a"] shouldBe "42"
        result["b"] shouldBe "foo"
    }

    @Test
    fun `notebook API inside renderer`() {
        val repl = makeRepl()
        repl.eval(
            """
            USE {
                render<Number> { "${"$"}{notebook?.currentCell?.internalId}. ${"$"}{it.toLong() * 10}" }
            }
            """.trimIndent()
        )

        assertEquals("1. 420", repl.eval("42.1").resultValue)
        assertEquals("2. 150", repl.eval("15").resultValue)
    }

    @Test
    fun `rendering processor should work fine`() {
        val repl = makeRepl()
        repl.eval(
            """
            class A
            class B(val a: A)
            
            USE {
                render<A> { "iA" }
                renderWithHost<B> { host, value -> "iB: " + notebook!!.renderersProcessor.renderValue(host, value.a) }
            }
            """.trimIndent()
        )

        val result = repl.eval("B(A())")
        assertEquals("iB: iA", result.resultValue)
    }

    @Test
    fun `code preprocessing`() {
        val repl = makeRepl()
        repl.eval(
            """
            USE {
                preprocessCode { it.replace('b', 'x') }
            }
            """.trimIndent()
        )

        val result = repl.eval("\"abab\"")
        assertEquals("axax", result.resultValue)
    }

    @Test
    fun `interruption callbacks`() {
        var x = 0
        val repl = makeRepl(
            "lib1" to library {
                onInterrupt { ++x }
            }
        )

        repl.eval("%use lib1")
        x shouldBe 0

        shouldThrow<ReplEvalRuntimeException> {
            repl.eval("throw java.lang.ThreadDeath()")
        }
        x shouldBe 1
    }

    @Test
    fun `color scheme change`() {
        var y = 2
        val repl = makeRepl(
            "lib1" to library {
                onColorSchemeChange { scheme ->
                    y = when (scheme) {
                        ColorScheme.LIGHT -> 3
                        ColorScheme.DARK -> 4
                    }
                }
            }
        )

        repl.eval("%use lib1")
        y shouldBe 2

        repl.notebook.changeColorScheme(ColorScheme.DARK)
        y shouldBe 4

        repl.eval("notebook.changeColorScheme(ColorScheme.LIGHT)")
        y shouldBe 3
    }
}
