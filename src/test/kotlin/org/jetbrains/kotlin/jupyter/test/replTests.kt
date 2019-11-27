package org.jetbrains.kotlin.jupyter.test

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.jetbrains.kotlin.jupyter.ReplForJupyter
import org.jetbrains.kotlin.jupyter.parseLibrariesConfig
import org.jetbrains.kotlin.jupyter.repl.completion.CompletionResultSuccess
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ReplTest {

    var repl = ReplForJupyter(classpath)

    @Before
    fun SetUp() {
        repl = ReplForJupyter(classpath)
    }

    @Test
    fun TestRepl() {
        repl.eval("val x = 3")
        var res = repl.eval("x*2")
        Assert.assertEquals(6, res.resultValue)
    }

    @Test
    fun TestDependsOnAnnotation() {
        repl.eval("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
    }

    @Test
    fun TestDependsOnAnnotations() {
        val sb = StringBuilder()
        sb.appendln("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
        sb.appendln("@file:Repository(\"https://repo.spring.io/libs-release\")")
        sb.appendln("@file:DependsOn(\"org.jetbrains.kotlinx:kotlinx.html.jvm:0.5.12\")")
        repl.eval(sb.toString())
    }

    @Test
    fun TestCompletion() {
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
    fun TestMagic() {
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
        val replConfig = parseLibrariesConfig(json)
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
}