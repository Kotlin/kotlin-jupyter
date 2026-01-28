package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import jupyter.kotlin.receivers.TempAnnotation
import jupyter.kotlin.variablesReport
import kotlinx.serialization.SerializationException
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion.Companion.toMaybeUnspecifiedString
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.MimeTypes
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.api.VariableDeclaration
import org.jetbrains.kotlinx.jupyter.api.declare
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourceType
import org.jetbrains.kotlinx.jupyter.api.textResult
import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplLibraryException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryDescriptor
import org.jetbrains.kotlinx.jupyter.test.evalError
import org.jetbrains.kotlinx.jupyter.test.evalRaw
import org.jetbrains.kotlinx.jupyter.test.evalRendered
import org.jetbrains.kotlinx.jupyter.test.evalRenderedError
import org.jetbrains.kotlinx.jupyter.test.library
import org.jetbrains.kotlinx.jupyter.test.toLibraries
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.reflect.typeOf

class CustomLibraryResolverTests : AbstractReplTest() {
    @Test
    fun testUseMagic() {
        val lib1 =
            "mylib" to
                $$"""
                {
                    "properties": {
                        "v1": "0.2"
                    },
                    "dependencies": [
                        "artifact1:$v1",
                        "artifact2:$v1"
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
        val lib2 =
            "other" to
                $$"""
                {
                    "properties": {
                        "a": "temp", 
                        "b": "test"
                    },
                    "repositories": [
                        "repo-$a"
                    ],
                    "dependencies": [
                        "path-$b"
                    ],
                    "imports": [
                        "otherPackage"
                    ],
                    "init": [
                        "otherInit"
                    ]
                }
                """.trimIndent()
        val lib3 =
            "another" to
                $$"""
                {
                    "properties": {
                        "v": "1" 
                    },
                    "dependencies": [
                        "anotherDep"
                    ],
                    "imports": [
                        "anotherPackage$v"
                    ],
                    "init": [
                        "%use other(b=release, a=debug)",
                        "anotherInit"
                    ]
                }
                """.trimIndent()

        val libs = listOf(lib1, lib2, lib3).toLibraries()

        val executor = makeReplWithLibraries(libs).mockExecution()

        executor.execute("%use mylib(1.0), another", ignoreDependencyErrors = true)
        val executedCodes = executor.executedCodes
        executedCodes.forEach(::println)

        val expectedCodes =
            arrayOf(
                """
                    import package1
                    import package2
                """,
                "code1",
                "code2",
                """
                    import anotherPackage1
                """,
                """
                    import otherPackage
                """,
                "otherInit",
                "anotherInit",
            ).map { it.trimIndent() }

        executedCodes shouldBe expectedCodes
    }

    @Test
    fun testLibraryOnShutdown() {
        val lib1 =
            "mylib" to
                """
                {
                    "shutdown": [
                        "14 * 3",
                        "throw RuntimeException()",
                        "21 + 22"
                    ]
                }
                """.trimIndent()

        val lib2 =
            "mylib2" to
                """
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

        results.size shouldBe 4
        results[0].resultValue shouldBe 42
        results[1].resultValue.shouldBeNull()
        results[2].resultValue shouldBe 43
        results[3].resultValue shouldBe 100
    }

    @Test
    fun testTransitiveRendering() {
        val lib =
            "mylib" to
                $$"""
                {
                    "renderers": {
                        "java.lang.String": "HTML(\"<b>\" + $it + \"</b>\")"
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

        val lib1 =
            "mylib" to
                """
                {
                    "minKernelVersion": "$minRequiredVersion"
                }
                """.trimIndent()

        val libs = listOf(lib1).toLibraries()
        val replWithResolver = makeReplWithLibraries(libs)
        val exception = replWithResolver.evalError<ReplPreprocessingException>("%use mylib")

        val message = exception.message!!
        message shouldContain minRequiredVersion
        message shouldContain kernelVersion
    }

    annotation class TestAnnotation

    @Test
    fun testExecutionOrder() {
        val lib1 =
            "lib1" to
                library {
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
        val lib2 =
            "lib2" to
                library {
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

        val code =
            """
            %use lib1
            5
            """.trimIndent()
        repl.execute(code)

        val expectedCodes =
            listOf(
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

        repl.executedCodes shouldBe expectedCodes

        val expectedResults = (1..5).toList()
        val actualResults = repl.results.filter { it != null && it != Unit }.map { it as Int }
        actualResults shouldBe expectedResults
    }

    // Test for https://youtrack.jetbrains.com/issue/KT-82503/K2-Repl-Nested-class-annotations-are-not-available-in-the-next-snippet
    @Test
    fun testClassAnnotations() {
        val lib =
            "lib" to
                library {
                    import<TestAnnotation>()
                    onClassAnnotation<TestAnnotation> { classes ->
                        val annotatedClassNames = classes.map { it.simpleName }.joinToString { "\"$it\"" }
                        scheduleExecution("val annotatedClasses = listOf($annotatedClassNames)")
                    }
                }
        val repl = makeReplWithLibraries(lib)
        val replWithTracker = makeReplWithLibraries(lib).trackExecution()

        replWithTracker.execute(
            """
            %use lib
            """.trimIndent(),
        )
        replWithTracker.execute(
            """
            @TestAnnotation
            class A
            @TestAnnotation
            interface B {
              @TestAnnotation
              interface C
              interface D {
                @TestAnnotation
                class E
              }
            }
            """.trimIndent(),
        )

        val res = replWithTracker.execute("annotatedClasses")

        when (repl.compilerMode) {
            ReplCompilerMode.K1 -> res.result.value shouldBe listOf("A", "B", "C", "E")
            ReplCompilerMode.K2 -> res.result.value shouldBe listOf("A", "B")
        }
    }

    @Test
    fun testFileAnnotations() {
        val lib =
            "lib" to
                library {
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
        val res =
            repl.execute(
                """
                b
                """.trimIndent(),
            )

        res.result.value shouldBe 1

        val expected =
            listOf(
                "1",
                "import jupyter.kotlin.receivers.TempAnnotation",
                "@file:TempAnnotation\nval a = 1",
                "val b = a",
                "b",
            )
        repl.executedCodes shouldBe expected
    }

    @Test
    fun testIncorrectDescriptors() {
        val ex1 =
            shouldThrow<ReplException> {
                parseLibraryDescriptor(
                    """
                    {
                        "imports": []
                    """.trimIndent(),
                )
            }
        ex1.cause.shouldBeInstanceOf<SerializationException>()

        shouldNotThrow<Throwable> {
            parseLibraryDescriptor("{}")
        }
    }

    @Test
    fun testLibraryWithResourcesDescriptorParsing() {
        val descriptor = parseLibraryDescriptor(File("src/test/testData/lib-with-resources.json").readText())
        val resources = descriptor.resources
        resources.size shouldBe 1

        val jsResource = resources.single()
        jsResource.type shouldBe ResourceType.JS
        val bundles = jsResource.bundles
        bundles.size shouldBe 2
        bundles[0].locations.size shouldBe 1
        bundles[1].locations.size shouldBe 1
    }

    @Test
    fun testLibraryWithIncorrectImport() {
        val e =
            shouldThrow<ReplLibraryException> {
                makeReplEnablingSingleLibrary(
                    library {
                        import("ru.incorrect")
                    },
                )
            }
        e.part shouldBe LibraryProblemPart.PREBUILT
    }

    @Test
    fun testLibraryWithIncorrectDependency() {
        val e =
            shouldThrow<ReplLibraryException> {
                makeReplEnablingSingleLibrary(
                    library {
                        dependencies("org.foo:bar:42")
                    },
                )
            }
        e.part shouldBe LibraryProblemPart.PREBUILT
    }

    @Test
    fun testLibraryWithIncorrectInitCode() {
        val e =
            shouldThrow<ReplLibraryException> {
                makeReplEnablingSingleLibrary(
                    library {
                        onLoaded {
                            null!!
                        }
                    },
                )
            }
        e.part shouldBe LibraryProblemPart.INIT
    }

    @Test
    fun testBeforeExecutionException() {
        val repl =
            makeReplEnablingSingleLibrary(
                library {
                    beforeCellExecution {
                        throw NullPointerException()
                    }
                },
            )

        val e =
            shouldThrow<ReplLibraryException> {
                repl.evalRaw("7")
            }
        e.part shouldBe LibraryProblemPart.BEFORE_CELL_CALLBACKS
    }

    @Test
    fun `multiple before-cell executions should be executed in the order of declaration`() {
        val builder = StringBuilder()

        val repl =
            makeReplEnablingSingleLibrary(
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
        val repl =
            makeReplEnablingSingleLibrary(
                library {
                    renderThrowable<IllegalArgumentException> { textResult(it.message.orEmpty()) }
                },
            )

        val display1 = repl.evalRenderedError<ReplEvalRuntimeException>("throw IllegalArgumentException(\"42\")")
        display1.shouldNotBeNull()
        val json = display1.toJson(overrideId = null).toString()
        json shouldBe """{"data":{"text/plain":"42"},"metadata":{}}"""

        repl.evalError<ReplEvalRuntimeException>("throw IndexOutOfBoundsException()")
    }

    @Test
    @ExperimentalStdlibApi
    fun testLibraryProperties() {
        val mutProp = arrayListOf(1)

        val repl =
            makeReplEnablingSingleLibrary(
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

        val result =
            repl.evalRaw(
                """
                x3.add(2)
                x1 + x2
                """.trimIndent(),
            )

        result shouldBe 42
        mutProp shouldBe listOf(1, 2)
    }

    @Test
    fun testInternalMarkers() {
        val repl =
            makeReplEnablingSingleLibrary(
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

        repl.notebook.variablesReport shouldBe
            """
            Visible vars: 
            ${'\t'}x1 : 22
            ${'\t'}a : 42
            
            """.trimIndent()
    }
}
