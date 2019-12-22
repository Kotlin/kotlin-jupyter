package org.jetbrains.kotlin.jupyter.test

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import jupyter.kotlin.MimeTypedResult
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.jetbrains.kotlin.jupyter.*
import org.jetbrains.kotlin.jupyter.repl.completion.CompletionResultSuccess
import org.junit.Assert
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull

class ReplTest {

    fun replWithResolver() = ReplForJupyter(classpath, ResolverConfig(defaultRepositories,
            parserLibraryDescriptors(readLibraries().toMap()).asDeferred()))

    @Test
    fun TestRepl() {
        val repl = ReplForJupyter(classpath)
        repl.eval("val x = 3")
        val res = repl.eval("x*2")
        assertEquals(6, res.resultValue)
    }

    @Test
    fun TestDependsOnAnnotation() {
        val repl = ReplForJupyter(classpath)
        repl.eval("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
    }

    @Test
    fun TestScriptIsolation() {
        val repl = ReplForJupyter(classpath)
        assertFails {
            repl.eval("org.jetbrains.kotlin.jupyter.ReplLineMagics.use")
        }
    }

    @Test
    fun TestDependsOnAnnotations() {
        val repl = ReplForJupyter(classpath)
        val sb = StringBuilder()
        sb.appendln("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
        sb.appendln("@file:Repository(\"https://repo.spring.io/libs-release\")")
        sb.appendln("@file:DependsOn(\"org.jetbrains.kotlinx:kotlinx.html.jvm:0.5.12\")")
        repl.eval(sb.toString())
    }

    @Test
    fun TestCompletion() {
        val repl = ReplForJupyter(classpath)
        repl.eval("val foobar = 42")
        repl.eval("var foobaz = 43")
        val result = repl.complete("val t = foo", 11)

        if (result is CompletionResultSuccess) {
            Assert.assertEquals(arrayListOf("foobar", "foobaz"), result.matches.sorted())
        } else {
            Assert.fail("Result should be success")
        }
    }

    @Test
    fun TestOut() {
        val repl = ReplForJupyter(classpath)
        repl.eval("1+1", 1)
        val res = repl.eval("Out[1]")
        assertEquals(2, res.resultValue)
        assertFails { repl.eval("Out[3]") }
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
                                    "dependencies": [
                                        "path-${'$'}a",
                                        "path-${'$'}b"
                                    ],
                                    "imports": [
                                        "otherPackage"
                                    ]
                                }
        """.trimIndent()
        val parser = Parser.default()

        val libJsons = arrayOf(lib1, lib2).map { it.first to parser.parse(StringBuilder(it.second)) as JsonObject }.toMap()

        val repl = ReplForJupyter(classpath, ResolverConfig(defaultRepositories, parserLibraryDescriptors(libJsons).asDeferred()))
        val res = repl.preprocessCode("%use mylib(1.0), other(b=release, a=debug)").trimIndent()
        val libs = repl.librariesCodeGenerator.getProcessedLibraries()
        assertEquals("", res)
        assertEquals(2, libs.count())
        arrayOf(
                """
                    @file:DependsOn("artifact1:1.0")
                    @file:DependsOn("artifact2:1.0")
                    import package1
                    import package2
                    code1
                    code2
                """,
                """
                    @file:DependsOn("path-debug")
                    @file:DependsOn("path-release")
                    import otherPackage
                """
        ).forEachIndexed { index, expected ->
            Assert.assertEquals(expected.trimIndent(), libs[index].code.trimEnd().convertCRLFtoLF())
        }
    }

    @Test
    fun TestLetsPlot() {
        val repl = replWithResolver()
        val code1 = "%use lets-plot"
        val code2 = """lets_plot(mapOf<String, Any>("cat" to listOf("a", "b")))"""
        val res1 = repl.eval(code1)
        Assert.assertEquals(1, res1.displayValues.count())
        Assert.assertNull(res1.resultValue)
        val res2 = repl.eval(code2)
        Assert.assertEquals(0, res2.displayValues.count())
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
        val res = repl.eval(code)
        assertEquals(1, res.displayValues.count())
    }

    @Test
    //TODO: https://github.com/Kotlin/kotlin-jupyter/issues/25
    fun TestKranglImportInfixFun() {
        val repl = replWithResolver()
        val code = """%use krangl
                        "a" to {it["a"]}"""
        val res = repl.eval(code)
        assertNotNull(res.resultValue)
    }

    private fun String.convertCRLFtoLF(): String {
        return replace("\r\n", "\n")
    }
}