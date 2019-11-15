package org.jetbrains.kotlin.jupyter.test

import org.jetbrains.kotlin.jupyter.ReplForJupyter
import org.jetbrains.kotlin.jupyter.repl.completion.CompletionResultSuccess
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ReplTest{

    var repl = ReplForJupyter()

    @Before
    fun SetUp(){
        repl = ReplForJupyter(classpath)
    }

    @Test
    fun TestRepl(){
        repl.eval("val x = 3")
        var res = repl.eval("x*2")
        Assert.assertEquals(6, res.resultValue)
    }

    @Test
    fun TestDependsOnAnnotation(){
        repl.eval("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
    }

    @Test
    fun TestDependsOnAnnotations(){
        val sb = StringBuilder()
        sb.appendln("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
        sb.appendln("@file:Repository(\"https://repo.spring.io/libs-release\")")
        sb.appendln("@file:DependsOn(\"org.jetbrains.kotlinx:kotlinx.html.jvm:0.5.12\")")
        repl.eval(sb.toString())
    }

    @Test
    fun TestDependsOnAlias() {
        repl.eval("@file:DependsOn(\"klaxon\")")
        repl.eval("val k = Klaxon()")
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
}