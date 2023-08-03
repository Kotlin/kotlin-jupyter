
package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import jupyter.kotlin.receivers.TempAnnotation
import jupyter.kotlin.variablesReport
import kotlinx.serialization.SerializationException
import org.jetbrains.kotlinx.jupyter.api.DisplayResult
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion.Companion.toMaybeUnspecifiedString
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.MimeTypes
import org.jetbrains.kotlinx.jupyter.api.VariableDeclaration
import org.jetbrains.kotlinx.jupyter.api.declare
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourceType
import org.jetbrains.kotlinx.jupyter.api.textResult
import org.jetbrains.kotlinx.jupyter.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplLibraryException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryDescriptor
import org.jetbrains.kotlinx.jupyter.test.evalRaw
import org.jetbrains.kotlinx.jupyter.test.evalRendered
import org.jetbrains.kotlinx.jupyter.test.library
import org.jetbrains.kotlinx.jupyter.test.toLibraries
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.reflect.typeOf
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomLibraryResolverTests : AbstractReplTest() {

    @Test
    fun testUseMagic() {
        val lib1 = "mylib" to """
                    {
                        "properties": {
                            "v1": "0.2"
                        },
                        "dependencies": [
                            "artifact1:${'$'}v1",
                            "artifact2:${'$'}v1"
                        ],
                        "imports": [
                            "package1",
                            "package2"
                        ],
                        "init": [
                            "code1",
                            "code2"
                        ]
                    }
        """.trimIndent()
        val lib2 = "other" to """
                                {
                                    "properties": {
                                        "a": "temp", 
                                        "b": "test"
                                    },
                                    "repositories": [
                                        "repo-${'$'}a"
                                    ],
                                    "dependencies": [
                                        "path-${'$'}b"
                                    ],
                                    "imports": [
                                        "otherPackage"
                                    ],
                                    "init": [
                                        "otherInit"
                                    ]
                                }
        """.trimIndent()
        val lib3 = "another" to """
                                            {
                                                "properties": {
                                                    "v": "1" 
                                                },
                                                "dependencies": [
                                                    "anotherDep"
                                                ],
                                                "imports": [
                                                    "anotherPackage${'$'}v"
                                                ],
                                                "init": [
                                                    "%use other(b=release, a=debug)",
                                                    "anotherInit"
                                                ]
                                            }
        """.trimIndent()

        val libs = listOf(lib1, lib2, lib3).toLibraries()

        val executor = makeReplWithLibraries(libs).mockExecution()

        executor.execute("%use mylib(1.0), another")
        val executedCodes = executor.executedCodes
        executedCodes.forEach(::println)

        val expectedCodes = arrayOf(
            """
                    @file:DependsOn("artifact1:1.0")
                    @file:DependsOn("artifact2:1.0")
                    import package1
                    import package2
                    """,
            "code1",
            "code2",
            """
                @file:DependsOn("anotherDep")
                import anotherPackage1
            """,
            """
                    @file:Repository("repo-debug")
                    @file:DependsOn("path-release")
                    import otherPackage
                    """,
            "otherInit",
            "anotherInit",
        ).map { it.trimIndent() }

        executedCodes shouldBe expectedCodes
    }

    @Test
    fun testLibraryOnShutdown() {
        val lib1 = "mylib" to """
                    {
                        "shutdown": [
                            "14 * 3",
                            "throw RuntimeException()",
                            "21 + 22"
                        ]
                    }
        """.trimIndent()

        val lib2 = "mylib2" to """
                    {
                        "shutdown": [
                            "100"
                        ]
                    }
        """.trimIndent()

        val libs = listOf(lib1, lib2).toLibraries()
        val replWithResolver = makeReplWithLibraries(libs)
        replWithResolver.evalRaw("%use mylib, mylib2")
        val results = replWithResolver.evalOnShutdown()

        assertEquals(4, results.size)
        assertEquals(42, results[0].resultValue)
        assertNull(results[1].resultValue)
        assertEquals(43, results[2].resultValue)
        assertEquals(100, results[3].resultValue)
    }

    @Test
    fun testTransitiveRendering() {
        val lib = "mylib" to """
            {
                "renderers": {
                    "java.lang.String": "HTML(\"<b>\" + ${"$"}it + \"</b>\")"
                }
            }
        """.trimIndent()

        val libs = listOf(lib).toLibraries()
        val repl = makeReplWithLibraries(libs)

        repl.evalRaw(
            """
            %use mylib
            USE {
                render<Int> { (it * 2).toString() }
            }
            """.trimIndent(),
        )

        val result = repl.evalRendered("13")

        result.shouldBeTypeOf<MimeTypedResult>()
        result[MimeTypes.HTML] shouldBe "<b>26</b>"
    }

    @Test
    fun testLibraryKernelVersionRequirements() {
        val minRequiredVersion = "999.42.0.1"
        val kernelVersion = defaultRuntimeProperties.version.toMaybeUnspecifiedString()

        val lib1 = "mylib" to """
                    {
                        "minKernelVersion": "$minRequiredVersion"
                    }
        """.trimIndent()

        val libs = listOf(lib1).toLibraries()
        val replWithResolver = makeReplWithLibraries(libs)
        val exception = assertThrows<ReplPreprocessingException> { replWithResolver.evalRaw("%use mylib") }

        val message = exception.message!!
        Assertions.assertTrue(message.contains(minRequiredVersion))
        Assertions.assertTrue(message.contains(kernelVersion))
    }

    annotation class TestAnnotation

    @Test
    fun testExecutionOrder() {
        val lib1 = "lib1" to library {
            onLoaded {
                scheduleExecution("3")
                execute(
                    """
                    %use lib2
                    EXECUTE("4")
                    @TestAnnotation
                    class Temp
                    """.trimIndent(),
                )
            }
            import("java.*")
        }
        val lib2 = "lib2" to library {
            onLoaded {
                execute(
                    """
                    EXECUTE("2")
                    1
                    """.trimIndent(),
                )
            }
            import<TestAnnotation>()
            onClassAnnotation<TestAnnotation> {
                execute(
                    """
                    EXECUTE("3")
                    """.trimIndent(),
                )
            }
        }

        val repl = makeReplWithLibraries(lib1, lib2).trackExecution()

        val code = """
            %use lib1
            5
        """.trimIndent()
        repl.execute(code)

        val expectedCodes = listOf(
            "import java.*",
            "import ${TestAnnotation::class.qualifiedName!!}",
            """
                EXECUTE("2")
                1
            """.trimIndent(),
            "2",
            """
                EXECUTE("4")
                @TestAnnotation
                class Temp
            """.trimIndent(),
            """EXECUTE("3")""",
            "3",
            "4",
            "5",
        )

        assertEquals(expectedCodes, repl.executedCodes)

        val expectedResults = (1..5).toList()
        val actualResults = repl.results.filter { it != null && it != Unit }.map { it as Int }
        assertEquals(expectedResults, actualResults)
    }

    @Test
    fun testFileAnnotations() {
        val lib = "lib" to library {
            import<TempAnnotation>()
            onFileAnnotation<TempAnnotation> {
                scheduleExecution("val b = a")
            }
        }
        val repl = makeReplWithLibraries(lib).trackExecution()

        repl.execute("1")

        repl.execute(
            """
            %use lib
            """.trimIndent(),
        )
        repl.execute(
            """
            @file:TempAnnotation
            val a = 1
            """.trimIndent(),
        )
        val res = repl.execute(
            """
            b
            """.trimIndent(),
        )

        assertEquals(1, res.result.value)

        val expected = listOf(
            "1",
            "import jupyter.kotlin.receivers.TempAnnotation",
            "@file:TempAnnotation\nval a = 1",
            "val b = a",
            "b",
        )
        assertEquals(expected, repl.executedCodes)
    }

    @Test
    fun testIncorrectDescriptors() {
        val ex1 = assertThrows<ReplException> {
            parseLibraryDescriptor(
                """
                {
                    "imports": []
                """.trimIndent(),
            )
        }
        assertTrue(ex1.cause is SerializationException)

        val ex2 = assertThrows<ReplException> {
            parseLibraryDescriptor(
                """
                {
                    "imports2": []
                }
                """.trimIndent(),
            )
        }
        assertTrue(ex2.cause is SerializationException)

        assertDoesNotThrow {
            parseLibraryDescriptor("{}")
        }
    }

    @Test
    fun testLibraryWithResourcesDescriptorParsing() {
        val descriptor = parseLibraryDescriptor(File("src/test/testData/lib-with-resources.json").readText())
        val resources = descriptor.resources
        assertEquals(1, resources.size)

        val jsResource = resources.single()
        assertEquals(ResourceType.JS, jsResource.type)
        val bundles = jsResource.bundles
        assertEquals(2, bundles.size)
        assertEquals(1, bundles[0].locations.size)
        assertEquals(1, bundles[1].locations.size)
    }

    @Test
    fun testLibraryWithIncorrectImport() {
        val e = assertThrows<ReplLibraryException> {
            makeReplEnablingSingleLibrary(
                library {
                    import("ru.incorrect")
                },
            )
        }
        assertEquals(LibraryProblemPart.PREBUILT, e.part)
    }

    @Test
    fun testLibraryWithIncorrectDependency() {
        val e = assertThrows<ReplLibraryException> {
            makeReplEnablingSingleLibrary(
                library {
                    dependencies("org.foo:bar:42")
                },
            )
        }
        assertEquals(LibraryProblemPart.PREBUILT, e.part)
    }

    @Test
    fun testLibraryWithIncorrectInitCode() {
        val e = assertThrows<ReplLibraryException> {
            makeReplEnablingSingleLibrary(
                library {
                    onLoaded {
                        null!!
                    }
                },
            )
        }
        assertEquals(LibraryProblemPart.INIT, e.part)
    }

    @Test
    fun testExceptionInRenderer() {
        val repl = makeReplEnablingSingleLibrary(
            library {
                render<String> { throw IllegalStateException() }
            },
        )

        repl.evalRaw("42")
        val res = assertDoesNotThrow {
            repl.evalRendered(
                """
                "42"
                """.trimIndent(),
            )
        }
        assertNull(res)
    }

    @Test
    fun testBeforeExecutionException() {
        val repl = makeReplEnablingSingleLibrary(
            library {
                beforeCellExecution {
                    throw NullPointerException()
                }
            },
        )

        val e = assertThrows<ReplLibraryException> {
            repl.evalRaw("7")
        }
        assertEquals(LibraryProblemPart.BEFORE_CELL_CALLBACKS, e.part)
    }

    @Test
    fun `multiple before-cell executions should be executed in the order of declaration`() {
        val builder = StringBuilder()

        val repl = makeReplEnablingSingleLibrary(
            library {
                beforeCellExecution {
                    builder.append("1")
                }
                beforeCellExecution {
                    builder.append("2")
                }
            },
        )

        repl.evalRaw("0")
        repl.evalRaw("0")

        builder.toString() shouldBe "1212"
    }

    @Test
    fun testExceptionRendering() {
        val repl = makeReplEnablingSingleLibrary(
            library {
                renderThrowable<IllegalArgumentException> { textResult(it.message.orEmpty()) }
            },
        )
        val processor = repl.throwableRenderersProcessor

        val e1 = assertThrows<ReplEvalRuntimeException> {
            repl.evalRaw("throw IllegalArgumentException(\"42\")")
        }
        val display1 = processor.renderThrowable(e1.cause!!)
        display1.shouldBeInstanceOf<DisplayResult>()
        val json = display1.toJson(overrideId = null).toString()
        json shouldBe """{"data":{"text/plain":"42"},"metadata":{}}"""

        val e2 = assertThrows<ReplEvalRuntimeException> {
            repl.evalRaw("throw IndexOutOfBoundsException()")
        }
        val display2 = processor.renderThrowable(e2.cause!!)
        display2 shouldBe null
    }

    @Test
    @ExperimentalStdlibApi
    fun testLibraryProperties() {
        val mutProp = arrayListOf(1)

        val repl = makeReplEnablingSingleLibrary(
            library {
                onLoaded {
                    declare(
                        "x1" to 22,
                        "x2" to 20,
                    )

                    declare(
                        VariableDeclaration("x3", mutProp, typeOf<ArrayList<Int>>()),
                    )
                }
            },
        )

        val result = repl.evalRaw(
            """
            x3.add(2)
            x1 + x2
            """.trimIndent(),
        )

        assertEquals(42, result)
        assertEquals(listOf(1, 2), mutProp)
    }

    @Test
    fun testInternalMarkers() {
        val repl = makeReplEnablingSingleLibrary(
            library {
                onLoaded {
                    declare(
                        "x1" to 22,
                        "_x2" to 20,
                    )
                }

                markVariableInternal {
                    it.name.startsWith("_")
                }
            },
        )

        repl.evalRaw(
            """
            var a = 42
            val _b = 11
            """.trimIndent(),
        )

        assertEquals(
            """
            Visible vars: 
            ${'\t'}x1 : 22
            ${'\t'}a : 42
            
            """.trimIndent(),
            repl.notebook.variablesReport,
        )
    }
}
