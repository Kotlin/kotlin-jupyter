package org.jetbrains.kotlin.jupyter.test

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import jupyter.kotlin.JavaRuntime
import jupyter.kotlin.MimeTypedResult
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.jupyter.*
import org.jetbrains.kotlin.jupyter.repl.completion.CompletionResult
import jupyter.kotlin.receivers.ConstReceiver
import org.jetbrains.kotlin.jupyter.repl.completion.ListErrorsResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.File
import kotlin.script.experimental.api.SourceCode
import kotlin.test.*

abstract class AbstractReplTest {
    protected fun<T> assertEq(expected: T, actual: T, message: String? = null) = assertEquals(expected, actual, message)

    protected fun String.convertCRLFtoLF(): String {
        return replace("\r\n", "\n")
    }
}

class ReplTest : AbstractReplTest() {
    @Test
    fun testRepl() {
        val repl = ReplForJupyterImpl(classpath)
        repl.eval("val x = 3")
        val res = repl.eval("x*2")
        assertEquals(6, res.resultValue)
    }

    @Test
    fun testPropertiesGeneration() {
        val repl = ReplForJupyterImpl(classpath)
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
        val repl = ReplForJupyterImpl(classpath)
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
        val repl = ReplForJupyterImpl(cp, null, ConstReceiver(value))
        val res = repl.eval("value")
        assertEquals(value, res.resultValue)
    }

    @Test
    fun testDependsOnAnnotation() {
        val repl = ReplForJupyterImpl(classpath)
        repl.eval("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
    }

    @Test
    fun testDependsOnAnnotationCompletion() {
        val repl = ReplForJupyterImpl(classpath)
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
        val repl = ReplForJupyterImpl(classpath)
        val res = repl.eval("""
            @file:DependsOn("src/test/testData/kernelTestPackage-1.0.jar")
            import pack.*
            func()
        """.trimIndent())

        assertEquals(42, res.resultValue)
    }

    @Test
    fun testScriptIsolation() {
        val repl = ReplForJupyterImpl(classpath)
        assertFails {
            repl.eval("org.jetbrains.kotlin.jupyter.ReplLineMagics.use")
        }
    }

    @Test
    fun testDependsOnAnnotations() {
        val repl = ReplForJupyterImpl(classpath)
        val sb = StringBuilder()
        sb.appendLine("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
        sb.appendLine("@file:Repository(\"https://repo.spring.io/libs-release\")")
        sb.appendLine("@file:DependsOn(\"org.jetbrains.kotlinx:kotlinx.html.jvm:0.5.12\")")
        repl.eval(sb.toString())
    }

    @Test
    fun testCompletionSimple() {
        val repl = ReplForJupyterImpl(classpath)
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
        val repl = ReplForJupyterImpl(classpath)

        runBlocking { repl.complete("val t = 42", 10) {
            result ->
            if (result is CompletionResult.Success) {
                assertEq(emptyList(), result.sortedMatches())
            } else {
                fail("Result should be success")
            }
        }
        }
    }

    @Test
    fun testCompletionForImplicitReceivers() {
        val repl = ReplForJupyterImpl(classpath)
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
        val repl = ReplForJupyterImpl(classpath)
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
    fun testEmptyErrorsListJson() {
        val res = ListErrorsResult("someCode")
        assertEquals("""{"errors":[],"code":"someCode"}""", res.toJson().toJsonString())
    }

    @Test
    fun testOut() {
        val repl = ReplForJupyterImpl(classpath)
        repl.eval("1+1", null, 1)
        val res = repl.eval("Out[1]")
        assertEquals(2, res.resultValue)
        assertFails { repl.eval("Out[3]") }
    }

    @Test
    fun testOutputMagic() {
        val repl = ReplForJupyterImpl(classpath)
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
        val parser = Parser.default()

        val libJsons = arrayOf(lib1, lib2, lib3).map { it.first to parser.parse(StringBuilder(it.second)) as JsonObject }.toMap()

        val repl = ReplForJupyterImpl(classpath, ResolverConfig(defaultRepositories, parserLibraryDescriptors(libJsons).asAsync()))
        val res = repl.preprocessCode("%use mylib(1.0), another")
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
    fun testJavaRuntimeUtils() {
        val repl = ReplForJupyterImpl(classpath)
        val result = repl.eval("JavaRuntimeUtils.version")
        val resultVersion = result.resultValue
        val expectedVersion = JavaRuntime.version
        assertEquals(expectedVersion, resultVersion)
    }
}

@Execution(ExecutionMode.SAME_THREAD)
class ReplWithResolverTest : AbstractReplTest() {
    private fun replWithResolver() = ReplForJupyterImpl(classpath, ResolverConfig(defaultRepositories,
            parserLibraryDescriptors(readLibraries().toMap()).asAsync()))

    @Test
    fun testLetsPlot() {
        val repl = replWithResolver()
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
        val repl = replWithResolver()
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
        val repl = replWithResolver()
        repl.eval("""%use krangl, lets-plot""")
        val res = repl.eval(""" "a" to {it["a"]} """)
        assertNotNull(res.resultValue)
    }

    @Test
    fun testNullableErasure() {
        val repl = replWithResolver()
        val code1 = "val a: Int? = 3"
        repl.eval(code1)
        val code2 = "a+2"
        val res = repl.eval(code2).resultValue
        assertEquals(5, res)
    }
}
