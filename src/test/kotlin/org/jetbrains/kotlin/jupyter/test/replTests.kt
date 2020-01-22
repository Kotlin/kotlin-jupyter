package org.jetbrains.kotlin.jupyter.test

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.jetbrains.kotlin.jupyter.ConstReceiver
import jupyter.kotlin.MimeTypedResult
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.jupyter.*
import org.jetbrains.kotlin.jupyter.repl.completion.CompletionResultSuccess
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.fail

class ReplTest {

    fun replWithResolver() = ReplForJupyterImpl(classpath, ResolverConfig(defaultRepositories,
            parserLibraryDescriptors(readLibraries().toMap()).asDeferred()))

    @Test
    fun TestRepl() {
        val repl = ReplForJupyterImpl(classpath)
        repl.eval("val x = 3")
        val res = repl.eval("x*2")
        assertEquals(6, res.resultValue)
    }

    @Test
    fun TestError() {
        val repl = ReplForJupyterImpl(classpath)
        try {
            repl.eval("""
                val foobar = 78
                val foobaz = "dsdsda"
                val ddd = ppp
                val ooo = foobar
            """.trimIndent())
        } catch (ex: ReplCompilerException) {
            val res = ex.errorResult
            val location = res.location ?: fail("Location should not be null")
            val message = res.message

            val expectedLocation = CompilerMessageLocation.create(location.path, 3, 11, 3, 14, location.lineContent)
            val expectedMessage = "Unresolved reference: ppp"

            assertEquals(expectedLocation, location)
            assertEquals(expectedMessage, message)

            return
        }

        fail("Test should fail with ReplCompilerException")
    }

    @Test
    fun TestReplWithReceiver() {
        val value = 5
        val cp = classpath + File(ConstReceiver::class.java.protectionDomain.codeSource.location.toURI().path)
        val repl = ReplForJupyterImpl(cp, null, ConstReceiver(value))
        val res = repl.eval("value")
        assertEquals(value, res.resultValue)
    }

    @Test
    fun TestDependsOnAnnotation() {
        val repl = ReplForJupyterImpl(classpath)
        repl.eval("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
    }

    @Test
    fun TestScriptIsolation() {
        val repl = ReplForJupyterImpl(classpath)
        assertFails {
            repl.eval("org.jetbrains.kotlin.jupyter.ReplLineMagics.use")
        }
    }

    @Test
    fun TestDependsOnAnnotations() {
        val repl = ReplForJupyterImpl(classpath)
        val sb = StringBuilder()
        sb.appendln("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
        sb.appendln("@file:Repository(\"https://repo.spring.io/libs-release\")")
        sb.appendln("@file:DependsOn(\"org.jetbrains.kotlinx:kotlinx.html.jvm:0.5.12\")")
        repl.eval(sb.toString())
    }

    @Test
    fun TestCompletion() {
        val repl = ReplForJupyterImpl(classpath)
        repl.eval("%use krangl-typed")
        repl.eval("val df = DataFrame.readCSV(\"C:\\\\Users\\\\Ilya.Muradyan\\\\jetstat.csv\")")
        val result = repl.complete("df.filter { D }", 13)

        if (result is CompletionResultSuccess) {
            Assert.assertEquals(arrayListOf("foobar", "foobaz"), result.matches.sorted())
        } else {
            Assert.fail("Result should be success")
        }
    }

    @Test
    fun TestOut() {
        val repl = ReplForJupyterImpl(classpath)
        repl.eval("1+1", null, 1)
        val res = repl.eval("Out[1]")
        assertEquals(2, res.resultValue)
        assertFails { repl.eval("Out[3]") }
    }

    @Test
    fun TestOutputMagic() {
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
    fun TestUseMagic() {
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

        val repl = ReplForJupyterImpl(classpath, ResolverConfig(defaultRepositories, parserLibraryDescriptors(libJsons).asDeferred()))
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
            Assert.assertEquals(expected.trimIndent(), res.initCodes[index].trimEnd().convertCRLFtoLF())
        }
    }

    @Test
    fun TestLetsPlot() {
        val repl = replWithResolver()
        val code1 = "%use lets-plot"
        val code2 = """lets_plot(mapOf<String, Any>("cat" to listOf("a", "b")))"""
        val displays = mutableListOf<Any>()
        fun displayHandler(display: Any) {
            displays.add(display)
        }

        val res1 = repl.eval(code1, ::displayHandler)
        Assert.assertEquals(1, displays.count())
        displays.clear()
        Assert.assertNull(res1.resultValue)
        val res2 = repl.eval(code2, ::displayHandler)
        Assert.assertEquals(0, displays.count())
        val mime = res2.resultValue as? MimeTypedResult
        assertNotNull(mime)
        assertEquals(1, mime.size)
        assertEquals("text/html", mime.entries.first().key)
        Assert.assertNotNull(res2.resultValue)
    }

    @Test
    fun TestTwoLibrariesInUse() {
        val repl = replWithResolver()
        val code = "%use lets-plot, krangl"
        val displays = mutableListOf<Any>()
        fun displayHandler(display: Any) {
            displays.add(display)
        }
        repl.eval(code, ::displayHandler)
        assertEquals(1, displays.count())
    }

    @Ignore
    @Test
    //TODO: https://github.com/Kotlin/kotlin-jupyter/issues/25
    fun TestKranglImportInfixFun() {
        val repl = replWithResolver()
        val code = """%use krangl
                        "a" to {it["a"]}"""
        val res = repl.eval(code)
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

    private fun String.convertCRLFtoLF(): String {
        return replace("\r\n", "\n")
    }
}