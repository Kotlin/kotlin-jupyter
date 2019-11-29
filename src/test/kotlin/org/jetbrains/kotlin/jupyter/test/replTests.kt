package org.jetbrains.kotlin.jupyter.test

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.jetbrains.kotlin.jupyter.ReplForJupyter
import org.jetbrains.kotlin.jupyter.parseResolverConfig
import org.jetbrains.kotlin.jupyter.readResolverConfig
import org.jetbrains.kotlin.jupyter.repl.completion.CompletionResultSuccess
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ReplTest {

    @Test
    fun TestRepl() {
        val repl = ReplForJupyter(classpath)
        repl.eval("val x = 3")
        var res = repl.eval("x*2")
        Assert.assertEquals(6, res.resultValue)
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
            repl.eval("""Thread.currentThread().contextClassLoader.loadClass("org.jetbrains.kotlin.jupyter.ReplForJupyter")""")
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
    fun TestUseMagic() {
        val config = """
            {
                "libraries": [
                    {
                        "name": "mylib(v1, v2=2.3)",
                        "artifacts": [
                            "artifact1:""" + "\$v1" + """",
                            "artifact2:""" + "\$v2" + """"
                        ],
                        "imports": [
                            "package1",
                            "package2"
                        ],
                        "init": [
                            "code1",
                            "code2"
                        ]
                    },
                    {
                        "name": "other(a=temp, b=test)",
                        "artifacts": [
                            "path-""" + "\$a" + """",
                            "path-""" + "\$b" + """"
                        ],
                    }
                ]
            }
        """.trimIndent()
        val json = Parser().parse(StringBuilder(config)) as JsonObject
        val replConfig = parseResolverConfig(json)
        val repl = ReplForJupyter(classpath, replConfig)
        val res = repl.codePreprocessor.process("%use mylib(1.0), other(b=release, a=debug)").trimIndent()
        val expected = """
            @file:DependsOn("artifact1:1.0")
            @file:DependsOn("artifact2:2.3")
            import package1
            import package2
            code1
            code2
            @file:DependsOn("path-debug")
            @file:DependsOn("path-release")
        """.trimIndent()
        Assert.assertEquals(expected, res)
    }

    @Test
    fun TestLetsPlot() {
        val config = readResolverConfig(File("libraries.json"))
        val repl = ReplForJupyter(classpath, config)
        val code1 = "%use lets-plot"
        val code2 = """ggplot(mapOf<String, Any>("cat" to listOf("a", "b")))"""
        val res1 = repl.eval(code1)
        Assert.assertEquals(1, res1.displayValues.count())
        Assert.assertNull(res1.resultValue)
        val res2 = repl.eval(code2)
        Assert.assertEquals(1, res2.displayValues.count())
        Assert.assertNotNull(res2.resultValue)
    }
}