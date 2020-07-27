package org.jetbrains.kotlin.jupyter.test

import jupyter.kotlin.JavaRuntime
import jupyter.kotlin.KotlinKernelVersion.Companion.toMaybeUnspecifiedString
import jupyter.kotlin.MimeTypedResult
import jupyter.kotlin.receivers.ConstReceiver
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.jupyter.GitHubRepoName
import org.jetbrains.kotlin.jupyter.GitHubRepoOwner
import org.jetbrains.kotlin.jupyter.LibrariesDir
import org.jetbrains.kotlin.jupyter.OutputConfig
import org.jetbrains.kotlin.jupyter.ReplCompilerException
import org.jetbrains.kotlin.jupyter.ReplEvalRuntimeException
import org.jetbrains.kotlin.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlin.jupyter.ResolverConfig
import org.jetbrains.kotlin.jupyter.defaultRepositories
import org.jetbrains.kotlin.jupyter.defaultRuntimeProperties
import org.jetbrains.kotlin.jupyter.generateDiagnostic
import org.jetbrains.kotlin.jupyter.generateDiagnosticFromAbsolute
import org.jetbrains.kotlin.jupyter.libraries.LibraryFactory
import org.jetbrains.kotlin.jupyter.libraries.LibraryResolutionInfo
import org.jetbrains.kotlin.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlin.jupyter.repl.CompletionResult
import org.jetbrains.kotlin.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlin.jupyter.withPath
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.File
import kotlin.script.experimental.api.SourceCode
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

abstract class AbstractReplTest {
    protected fun String.convertCRLFtoLF(): String {
        return replace("\r\n", "\n")
    }

    companion object {
        @JvmStatic
        protected val libraryFactory = LibraryFactory(LibraryResolutionInfo.ByNothing())

        @JvmStatic
        protected val homeDir = File("")
    }
}

class ReplTest : AbstractReplTest() {
    private val repl = ReplForJupyterImpl(libraryFactory, classpath)

    @Test
    fun testRepl() {
        repl.eval("val x = 3")
        val res = repl.eval("x*2")
        assertEquals(6, res.resultValue)
    }

    @Test
    fun testPropertiesGeneration() {
        // Note, this test should actually fail with ReplEvalRuntimeException, but 'cause of eval/compile
        // histories are out of sync, it fails with another exception. This test shows the wrong behavior and
        // should be fixed after fixing https://youtrack.jetbrains.com/issue/KT-36397

        // In fact, this shouldn't compile, but because of bug in compiler it fails in runtime
        assertThrows<ReplEvalRuntimeException> {
            repl.eval("""
                fun stack(vararg tup: Int): Int = tup.sum()
                val X = 1
                val x = stack(1, X)
            """.trimIndent())

            print("")
        }
    }

    @Test
    fun testError() {
        try {
            repl.eval("""
                val foobar = 78
                val foobaz = "dsdsda"
                val ddd = ppp
                val ooo = foobar
            """.trimIndent())
        } catch (ex: ReplCompilerException) {
            val diag = ex.firstDiagnostics
            val location = diag?.location ?: fail("Location should not be null")
            val message = ex.message

            val expectedLocation = SourceCode.Location(SourceCode.Position(3, 11), SourceCode.Position(3, 14))
            val expectedMessage = "Line_0.jupyter.kts (3:11 - 14) Unresolved reference: ppp"

            assertEquals(expectedLocation, location)
            assertEquals(expectedMessage, message)

            return
        }

        fail("Test should fail with ReplCompilerException")
    }

    @Test
    fun testReplWithReceiver() {
        val value = 5
        val cp = classpath + File(ConstReceiver::class.java.protectionDomain.codeSource.location.toURI().path)
        val repl = ReplForJupyterImpl(libraryFactory, cp, null, scriptReceivers = listOf(ConstReceiver(value)))
        val res = repl.eval("value")
        assertEquals(value, res.resultValue)
    }

    @Test
    fun testDependsOnAnnotation() {
        repl.eval("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
    }

    @Test
    fun testDependsOnAnnotationCompletion() {
        repl.eval("""
            @file:Repository("https://repo1.maven.org/maven2/")
            @file:DependsOn("com.github.doyaaaaaken:kotlin-csv-jvm:0.7.3")
        """.trimIndent())

        val res = runBlocking {
            var res2: CompletionResult? = null
            repl.complete("import com.github.", 18) { res2 = it }
            res2
        }
        when(res) {
            is CompletionResult.Success -> res.sortedMatches().contains("doyaaaaaken")
            else -> fail("Completion should be successful")
        }
    }

    @Test
    fun testExternalStaticFunctions() {
        val res = repl.eval("""
            @file:DependsOn("src/test/testData/kernelTestPackage-1.0.jar")
            import pack.*
            func()
        """.trimIndent())

        assertEquals(42, res.resultValue)
    }

    @Test
    fun testScriptIsolation() {
        assertFails {
            repl.eval("org.jetbrains.kotlin.jupyter.ReplLineMagics.use")
        }
    }

    @Test
    fun testDependsOnAnnotations() {
        val sb = StringBuilder()
        sb.appendLine("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
        sb.appendLine("@file:Repository(\"https://repo.spring.io/libs-release\")")
        sb.appendLine("@file:DependsOn(\"org.jetbrains.kotlinx:kotlinx.html.jvm:0.5.12\")")
        repl.eval(sb.toString())
    }

    @Test
    fun testCompletionSimple() {
        repl.eval("val foobar = 42")
        repl.eval("var foobaz = 43")

        runBlocking { repl.complete("val t = foo", 11) {
            result ->
                if (result is CompletionResult.Success) {
                    assertEquals(arrayListOf("foobar", "foobaz"), result.sortedMatches())
                } else {
                    fail("Result should be success")
                }
            }
        }
    }

    @Test
    fun testNoCompletionAfterNumbers() {
        runBlocking { repl.complete("val t = 42", 10) {
            result ->
            if (result is CompletionResult.Success) {
                assertEquals(emptyList(), result.sortedMatches())
            } else {
                fail("Result should be success")
            }
        }
        }
    }

    @Test
    fun testCompletionForImplicitReceivers() {
        repl.eval("""
            class AClass(val c_prop_x: Int) {
                fun filter(xxx: (AClass).() -> Boolean): AClass {
                    return this
                }
            }
            val AClass.c_prop_y: Int
                get() = c_prop_x * c_prop_x
            
            fun AClass.c_meth_z(v: Int) = v * c_prop_y
            val df = AClass(10)
            val c_zzz = "some string"
        """.trimIndent())
        runBlocking {
            repl.complete("df.filter { c_ }", 14) { result ->
                if (result is CompletionResult.Success) {
                    assertEquals(arrayListOf("c_meth_z(", "c_prop_x", "c_prop_y", "c_zzz"), result.sortedMatches())
                } else {
                    fail("Result should be success")
                }
            }
        }
    }

    @Test
    fun testErrorsList() {
        repl.eval("""
            data class AClass(val memx: Int, val memy: String)
            data class BClass(val memz: String, val mema: AClass)
            val foobar = 42
            var foobaz = "string"
            val v = BClass("KKK", AClass(5, "25"))
        """.trimIndent())
        runBlocking {
            repl.listErrors("""
                val a = AClass("42", 3.14)
                val b: Int = "str"
                val c = foob
            """.trimIndent()) {result ->
                val actualErrors = result.errors.toList()
                val path = actualErrors.first().sourcePath
                assertEquals(withPath(path, listOf(
                        generateDiagnostic(1, 16, 1, 20, "Type mismatch: inferred type is String but Int was expected", "ERROR"),
                        generateDiagnostic(1, 22, 1, 26, "The floating-point literal does not conform to the expected type String", "ERROR"),
                        generateDiagnostic(2, 14, 2, 19, "Type mismatch: inferred type is String but Int was expected", "ERROR"),
                        generateDiagnostic(3, 9, 3, 13, "Unresolved reference: foob", "ERROR")
                )), actualErrors)
            }
        }
    }

    @Test
    fun testErrorsListWithMagic() {
        runBlocking {
            repl.listErrors("""
                %use krangl
                
                val x = foobar
                3 * 14
                %trackClasspath
            """.trimIndent()) { result ->
                val actualErrors = result.errors.toList()
                val path = actualErrors.first().sourcePath
                assertEquals(withPath(path, listOf(
                        generateDiagnostic(3, 9, 3, 15, "Unresolved reference: foobar", "ERROR")
                )), actualErrors)
            }
        }
    }

    @Test
    fun testCompletionWithMagic() {
        repl.eval("val foobar = 42")

        runBlocking {
            val code = """
                    
                %trackClasspath
            
                foo
            """.trimIndent()
            repl.complete(code, code.indexOf("foo") + 3) { result ->
                if (result is CompletionResult.Success) {
                    assertEquals(arrayListOf("foobar"), result.sortedMatches())
                } else {
                    fail("Result should be success")
                }
            }
        }
    }

    @Test
    fun testCommands() {
        val code1 = ":help"
        val code2 = ":hex "

        runBlocking {
            repl.listErrors(code1) { result ->
                assertEquals(code1, result.code)
                assertEquals(0, result.errors.toList().size)
            }
            repl.listErrors(code2) { result ->
                assertEquals(code2, result.code)
                val expectedList = listOf(generateDiagnosticFromAbsolute(code2, 0, 4, "Unknown command", "ERROR"))
                val actualList = result.errors.toList()
                assertEquals(expectedList, actualList)
            }
            repl.complete(code2, 3) { result ->
                if (result is CompletionResult.Success) {
                    assertEquals(listOf("help"), result.sortedMatches())
                } else {
                    fail("Result should be success")
                }
            }
        }
    }

    @Test
    fun testEmptyErrorsListJson() {
        val res = ListErrorsResult("someCode")
        assertEquals("""{"errors":[],"code":"someCode"}""", res.toJson().toJsonString())
    }

    @Test
    fun testOut() {
        repl.eval("1+1", null, 1)
        val res = repl.eval("Out[1]")
        assertEquals(2, res.resultValue)
        assertFails { repl.eval("Out[3]") }
    }

    @Test
    fun testOutputMagic() {
        repl.preprocessCode("%output --max-cell-size=100500 --no-stdout")
        assertEquals(OutputConfig(
                cellOutputMaxSize = 100500,
                captureOutput = false
        ), repl.outputConfig)

        repl.preprocessCode("%output --max-buffer=42 --max-buffer-newline=33 --max-time=2000")
        assertEquals(OutputConfig(
                cellOutputMaxSize = 100500,
                captureOutput = false,
                captureBufferMaxSize = 42,
                captureNewlineBufferSize = 33,
                captureBufferTimeLimitMs = 2000
        ), repl.outputConfig)

        repl.preprocessCode("%output --reset-to-defaults")
        assertEquals(OutputConfig(), repl.outputConfig)
    }

    @Test
    fun testJavaRuntimeUtils() {
        val result = repl.eval("JavaRuntimeUtils.version")
        val resultVersion = result.resultValue
        val expectedVersion = JavaRuntime.version
        assertEquals(expectedVersion, resultVersion)
    }

    @Test
    fun testKotlinMath() {
        val result = repl.eval("2.0.pow(2.0)").resultValue
        assertEquals(4.0, result)
    }
}

class CustomLibraryResolverTests : AbstractReplTest() {
    private fun makeRepl(libs: LibraryResolver) = ReplForJupyterImpl(libraryFactory, classpath, homeDir, ResolverConfig(defaultRepositories, libs))

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
                    }""".trimIndent()
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

        val libs = listOf(lib1, lib2, lib3).toLibraries(libraryFactory)

        val replWithResolver = makeRepl(libs)
        val res = replWithResolver.preprocessCode("%use mylib(1.0), another")
        assertEquals("", res.code)
        val inits = arrayOf(
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
        )
        assertEquals(inits.count(), res.initCodes.count())
        inits.forEachIndexed { index, expected ->
            assertEquals(expected.trimIndent(), res.initCodes[index].trimEnd().convertCRLFtoLF())
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
                    }""".trimIndent()

        val lib2 = "mylib2" to """
                    {
                        "shutdown": [
                            "100"
                        ]
                    }""".trimIndent()

        val libs = listOf(lib1, lib2).toLibraries(libraryFactory)
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
                    }""".trimIndent()

        val libs = listOf(lib1).toLibraries(libraryFactory)
        val replWithResolver = makeRepl(libs)
        val exception = assertThrows<ReplCompilerException> { replWithResolver.eval("%use mylib") }

        val message = exception.message!!
        assertTrue(message.contains(minRequiredVersion))
        assertTrue(message.contains(kernelVersion))
    }
}

@Execution(ExecutionMode.SAME_THREAD)
class ReplWithResolverTest : AbstractReplTest() {
    private val repl = ReplForJupyterImpl(libraryFactory, classpath, homeDir, resolverConfig)

    private fun getReplWithStandardResolver(): ReplForJupyterImpl {
        val standardLibraryFactory = LibraryFactory.withDefaultDirectoryResolution(homeDir.resolve(LibrariesDir))
        val config = ResolverConfig(defaultRepositories, standardLibraryFactory.getStandardResolver("."))
        return ReplForJupyterImpl(standardLibraryFactory, classpath, homeDir, config, standardResolverRuntimeProperties)
    }

    @Test
    fun testLetsPlot() {
        val code1 = "%use lets-plot"
        val code2 = """lets_plot(mapOf<String, Any>("cat" to listOf("a", "b")))"""
        val displays = mutableListOf<Any>()
        fun displayHandler(display: Any) {
            displays.add(display)
        }

        val res1 = repl.eval(code1, ::displayHandler)
        assertEquals(1, displays.count())
        displays.clear()
        assertNull(res1.resultValue)
        val res2 = repl.eval(code2, ::displayHandler)
        assertEquals(0, displays.count())
        val mime = res2.resultValue as? MimeTypedResult
        assertNotNull(mime)
        assertEquals(1, mime.size)
        assertEquals("text/html", mime.entries.first().key)
        assertNotNull(res2.resultValue)
    }

    @Test
    fun testTwoLibrariesInUse() {
        val code = "%use lets-plot, krangl"
        val displays = mutableListOf<Any>()
        fun displayHandler(display: Any) {
            displays.add(display)
        }
        repl.eval(code, ::displayHandler)
        assertEquals(1, displays.count())
    }

    @Test
    fun testKranglImportInfixFun() {
        repl.eval("""%use krangl, lets-plot""")
        val res = repl.eval(""" "a" to {it["a"]} """)
        assertNotNull(res.resultValue)
    }

    @Test
    fun testStandardLibraryResolver() {
        val repl = getReplWithStandardResolver()

        val res = repl.eval("""
            %use krangl(0.13)
            val df = DataFrame.readCSV("src/test/testData/resolve-with-runtime.csv")
            df.head().rows.first().let { it["name"].toString() + " " + it["surname"].toString() }
        """.trimIndent())
        assertEquals("John Smith", res.resultValue)
    }

    @Test
    fun testDefaultInfoSwitcher() {
        val repl = getReplWithStandardResolver()

        val initialDefaultResolutionInfo = repl.libraryFactory.defaultResolutionInfo
        assertTrue(initialDefaultResolutionInfo is LibraryResolutionInfo.ByDir)

        repl.eval("%useLatestDescriptors")
        assertTrue(repl.libraryFactory.defaultResolutionInfo is LibraryResolutionInfo.ByGitRef)

        repl.eval("%useLatestDescriptors -off")
        assertTrue(repl.libraryFactory.defaultResolutionInfo === initialDefaultResolutionInfo)
    }

    @Test
    fun testUseFileUrlRef() {
        val repl = getReplWithStandardResolver()

        val commit = "1f56d74a88f6fb78306d685d0b3aaf07113a8abf"
        val libraryPath = "src/test/testData/test-init.json"

        val res1 = repl.eval("""
            %use @file[$libraryPath](name=x, value=42)
            x
        """.trimIndent())
        assertEquals(42, res1.resultValue)

        val res2 = repl.eval("""
            %use @url[https://raw.githubusercontent.com/$GitHubRepoOwner/$GitHubRepoName/$commit/$libraryPath](name=y, value=43)
            y
        """.trimIndent())
        assertEquals(43, res2.resultValue)

        val displays = mutableListOf<Any>()
        val res3 = repl.eval("%use lets-plot@$commit", { displays.add(it) })
        assertEquals(1, displays.count())
        assertNull(res3.resultValue)
        displays.clear()
    }

    @Test
    fun testRuntimeDepsResolution() {
        val res = repl.eval("""
            %use krangl(0.13)
            val df = DataFrame.readCSV("src/test/testData/resolve-with-runtime.csv")
            df.head().rows.first().let { it["name"].toString() + " " + it["surname"].toString() }
        """.trimIndent())
        assertEquals("John Smith", res.resultValue)
    }

    @Test
    fun testNullableErasure() {
        val code1 = "val a: Int? = 3"
        repl.eval(code1)
        val code2 = "a+2"
        val res = repl.eval(code2).resultValue
        assertEquals(5, res)
    }

    @Test
    fun testKlaxonClasspathDoesntLeak() {
        val res = repl.eval("""
            %use klaxon(2.1.8)
            class Person (val name: String, var age: Int = 23)
            val klaxon = Klaxon()
            val parseRes = klaxon.parse<Person>(""${'"'}
                {
                  "name": "John Smith"
                }
                ""${'"'})
            parseRes?.age
        """.trimIndent())
        assertEquals(23, res.resultValue)
    }

    companion object {
        val resolverConfig = libraryFactory.testResolverConfig
    }
}
