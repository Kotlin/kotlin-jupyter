
package org.jetbrains.kotlinx.jupyter.test.repl

import jupyter.kotlin.receivers.TempAnnotation
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion.Companion.toMaybeUnspecifiedString
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourceType
import org.jetbrains.kotlinx.jupyter.compiler.util.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.config.defaultRepositories
import org.jetbrains.kotlinx.jupyter.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.dependencies.ResolverConfig
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptor
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.test.library
import org.jetbrains.kotlinx.jupyter.test.toLibraries
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomLibraryResolverTests : AbstractReplTest() {

    private fun makeRepl(vararg libs: Pair<String, LibraryDefinition>) = makeRepl(libs.toList().toLibraries())

    private fun makeRepl(libs: LibraryResolver) = ReplForJupyterImpl(
        resolutionInfoProvider,
        classpathWithTestLib,
        homeDir,
        ResolverConfig(
            defaultRepositories,
            libs
        )
    )

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

        val executor = makeRepl(libs).mockExecution()

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
            "anotherInit"
        ).map { it.trimIndent() }

        assertEquals(expectedCodes.count(), executedCodes.count())
        expectedCodes.forEachIndexed { index, expected ->
            assertEquals(expected.trimIndent(), executedCodes[index])
        }
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
        val replWithResolver = makeRepl(libs)
        replWithResolver.eval("%use mylib, mylib2")
        val results = replWithResolver.evalOnShutdown()

        assertEquals(4, results.size)
        assertEquals(42, results[0].resultValue)
        assertNull(results[1].resultValue)
        assertEquals(43, results[2].resultValue)
        assertEquals(100, results[3].resultValue)
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
        val replWithResolver = makeRepl(libs)
        val exception = assertThrows<ReplCompilerException> { replWithResolver.eval("%use mylib") }

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
                    """.trimIndent()
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
                    """.trimIndent()
                )
            }
            import<TestAnnotation>()
            onClassAnnotation<TestAnnotation> {
                execute(
                    """
                    EXECUTE("3")
                    """.trimIndent()
                )
            }
        }

        val repl = makeRepl(lib1, lib2).trackExecution()

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
            "5"
        )

        assertEquals(expectedCodes, repl.executedCodes)

        val expectedResults = (1..5).toList()
        val actualResults = repl.results.filter { it != null && it != Unit }.map { it as Int }
        assertEquals(expectedResults, actualResults)
    }

    @Test
    @Disabled // TODO: waiting for fix https://youtrack.jetbrains.com/issue/KT-44580
    fun testFileAnnotations() {
        val lib = "lib" to library {
            import<TempAnnotation>()
            onFileAnnotation<TempAnnotation> {
                scheduleExecution("val b = a")
            }
        }
        val repl = makeRepl(lib).trackExecution()

        repl.execute("1")

        repl.execute(
            """
            %use lib
            """.trimIndent()
        )
        repl.execute(
            """
            @file:TempAnnotation
            val a = 1
            """.trimIndent()
        )
        val res = repl.execute(
            """
            b
            """.trimIndent()
        )

        assertEquals(1, res.result.value)

        val expected = listOf(
            "1",
            "@file:TempAnnotation\nval a = 1",
            "val b = a",
            "b"
        )
        assertEquals(expected, repl.executedCodes)
    }

    @Test
    fun testLibraryWithResourcesDescriptorParsing() {
        val descriptor = Json.decodeFromString<LibraryDescriptor>(File("src/test/testData/lib-with-resources.json").readText())
        val resources = descriptor.resources
        assertEquals(1, resources.size)

        val jsResource = resources.single()
        assertEquals(ResourceType.JS, jsResource.type)
        val bundles = jsResource.bundles
        assertEquals(2, bundles.size)
        assertEquals(1, bundles[0].locations.size)
        assertEquals(1, bundles[1].locations.size)
    }
}
