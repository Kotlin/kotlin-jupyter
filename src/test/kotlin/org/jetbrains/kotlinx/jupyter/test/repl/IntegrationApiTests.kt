package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlinx.jupyter.EvalRequestData
import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.api.CodePreprocessor
import org.jetbrains.kotlinx.jupyter.api.DeclarationKind
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.MimeTypes
import org.jetbrains.kotlinx.jupyter.api.Renderable
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.createLibrary
import org.jetbrains.kotlinx.jupyter.api.libraries.mavenLocal
import org.jetbrains.kotlinx.jupyter.api.libraries.repositories
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.buildDependenciesInitCode
import org.jetbrains.kotlinx.jupyter.repl.creating.createRepl
import org.jetbrains.kotlinx.jupyter.test.TestDisplayHandler
import org.jetbrains.kotlinx.jupyter.test.classpath
import org.jetbrains.kotlinx.jupyter.test.evalEx
import org.jetbrains.kotlinx.jupyter.test.evalRaw
import org.jetbrains.kotlinx.jupyter.test.evalRendered
import org.jetbrains.kotlinx.jupyter.test.library
import org.jetbrains.kotlinx.jupyter.test.testRepositories
import org.jetbrains.kotlinx.jupyter.test.toLibraries
import org.jetbrains.kotlinx.jupyter.util.EMPTY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties

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
        repl.evalRaw(code1)
        assertEquals(3, repl.evalRaw("l.value2"))

        // create list 'q' of the same size 3
        repl.evalRaw("val q = l.asReversed()")
        assertEquals(1, repl.evalRaw("q.value2"))

        // check that 'l' and 'q' have the same types
        assertEquals(
            3,
            repl.evalRaw(
                """var a = l
            a = q
            a.value0
                """.trimMargin(),
            ),
        )

        // create a list of size 6
        repl.evalRaw("val w = l + a")
        assertEquals(3, repl.evalRendered("w.value3"))

        // check that 'value3' is not available for list 'l'
        assertThrows<ReplCompilerException> {
            repl.evalRaw("l.value3")
        }

        repl.evalRaw("val e: List<Int>? = w.take(5)")
        val res = repl.evalRendered("e")

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
    fun `code preprocessors`() {
        val lib = "mylib" to library {
            addCodePreprocessor(
                object : CodePreprocessor {
                    override fun process(code: String, host: KotlinKernelHost): CodePreprocessor.Result {
                        return CodePreprocessor.Result(code.replace("2+2", "3+3"))
                    }

                    override fun accepts(code: String): Boolean {
                        return code == "2+2"
                    }
                },
            )

            addCodePreprocessor(
                object : CodePreprocessor {
                    override fun process(code: String, host: KotlinKernelHost): CodePreprocessor.Result {
                        return CodePreprocessor.Result(code.replace("1", "2"))
                    }
                },
            )
        }
        val repl = makeRepl(lib).trackExecution()

        repl.execute("%use mylib")

        val result = repl.execute("1+1").result.value
        result shouldBe 6
    }

    @Test
    fun `result code preprocessor`() {
        val displays = mutableListOf<Any>()
        val repl = createRepl(EmptyResolutionInfoProvider, classpath, null, testRepositories, displayHandler = TestDisplayHandler(displays))

        repl.eval {
            addLibrary(
                createLibrary(repl.notebook) {
                    afterCellExecution { snippetInstance, result ->
                        @Suppress("UNCHECKED_CAST")
                        val kClass: KClass<Any> = snippetInstance::class as KClass<Any>
                        if (result.name != null) return@afterCellExecution

                        val cellDeclarations = notebook.currentCell!!.declarations
                        val propNamesWithOrder = cellDeclarations
                            .filter { it.kind == DeclarationKind.PROPERTY }
                            .withIndex()
                            .associate {
                                it.value.name to it.index
                            }

                        val props = kClass.declaredMemberProperties
                        val lastProp = props.maxByOrNull { prop ->
                            propNamesWithOrder[prop.name] ?: -1
                        } ?: return@afterCellExecution

                        lastProp.get(snippetInstance)?.let { this.display(it, null) }
                    }
                },
            )
        }

        repl.evalEx(
            """
            val xyz=7
            val abc = 9
            val po = 97
            """.trimIndent(),
        )

        repl.evalEx("var `Russia is a terrorist state` = true")

        displays shouldBe listOf(97, true)
    }

    @Test
    fun `renderable objects`() {
        val repl = makeRepl()
        repl.evalRaw(
            """
            @file:DependsOn("src/test/testData/kotlin-jupyter-api-test-0.0.16.jar")
            """.trimIndent(),
        )

        val res = repl.evalRendered(
            """
            ses.visualizeColor("red")
            """.trimIndent(),
        )

        val result = res as Renderable
        val json = result.render(repl.notebook).toJson(Json.EMPTY, null)
        val jsonData = json["data"] as JsonObject
        val htmlString = jsonData[MimeTypes.HTML] as JsonPrimitive
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
            """.trimIndent(),
        )
        val repl = makeRepl(libs.toLibraries())
        repl.evalRaw("%use lib(a = 42, b=foo)")

        val res = repl.evalRaw("integrationOptions")
        res.shouldBeInstanceOf<Map<String, String>>()

        res["a"] shouldBe "42"
        res["b"] shouldBe "foo"
    }

    @Test
    fun `notebook API inside renderer`() {
        val repl = makeRepl()
        repl.evalRaw(
            """
            USE {
                render<Number> { "${"$"}{notebook?.currentCell?.internalId}. ${"$"}{it.toLong() * 10}" }
            }
            """.trimIndent(),
        )

        assertEquals("1. 420", repl.evalRendered("42.1"))
        assertEquals("2. 150", repl.evalRendered("15"))
    }

    @Test
    fun `rendering processor should work fine`() {
        val repl = makeRepl()
        repl.evalRaw(
            """
            class A
            class B(val a: A)
            
            USE {
                render<A> { "iA" }
                renderWithHost<B> { host, value -> "iB: " + notebook!!.renderersProcessor.renderValue(host, value.a) }
            }
            """.trimIndent(),
        )

        val result = repl.evalRendered("B(A())")
        assertEquals("iB: iA", result)
    }

    @Test
    fun `code preprocessing`() {
        val repl = makeRepl()
        repl.evalRaw(
            """
            USE {
                preprocessCode { it.replace('b', 'x') }
            }
            """.trimIndent(),
        )

        val result = repl.evalEx(EvalRequestData("\"abab\""))
        assertEquals("axax", result.rawValue)
    }

    @Test
    fun `interruption callbacks`() {
        var x = 0
        val repl = makeRepl(
            "lib1" to library {
                onInterrupt { ++x }
            },
        )

        repl.evalRaw("%use lib1")
        x shouldBe 0

        shouldThrow<ReplEvalRuntimeException> {
            repl.evalRaw("throw java.lang.ThreadDeath()")
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
            },
        )

        repl.evalRaw("%use lib1")
        y shouldBe 2

        repl.notebook.changeColorScheme(ColorScheme.DARK)
        y shouldBe 4

        repl.evalRaw("notebook.changeColorScheme(ColorScheme.LIGHT)")
        y shouldBe 3
    }

    @Test
    fun `repositories with auth should generate correct code`() {
        val lib = library {
            repositories {
                maven {
                    url = "sftp://repo.mycompany.com:22/repo"
                    credentials {
                        username = "xx\"y"
                        password = "678\n9"
                    }
                }

                mavenLocal()
            }

            dependencies("my.group:dep:42")
        }

        val code = buildDependenciesInitCode(listOf(lib))

        code shouldBe """
            @file:Repository("*mavenLocal")
            @file:Repository("sftp://repo.mycompany.com:22/repo", username="xx\"y", password="678\n9")
            @file:DependsOn("my.group:dep:42")
            
        """.trimIndent()
    }
}
