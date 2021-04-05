package org.jetbrains.kotlinx.jupyter.test.repl

import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.config.defaultRepositories
import org.jetbrains.kotlinx.jupyter.dependencies.ResolverConfig
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.test.classpath
import org.jetbrains.kotlinx.jupyter.test.library
import org.jetbrains.kotlinx.jupyter.test.toLibraries
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IntegrationApiTests {

    private fun makeRepl(vararg libs: Pair<String, LibraryDefinition>): ReplForJupyterImpl {
        val config = ResolverConfig(
            defaultRepositories,
            libs.toList().toLibraries()
        )
        return ReplForJupyterImpl(EmptyResolutionInfoProvider, classpath, null, config)
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
}
