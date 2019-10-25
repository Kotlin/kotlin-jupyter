package org.jetbrains.kotlin.jupyter.test

import org.jetbrains.kotlin.jupyter.ReplForJupyter
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
        repl.eval(1, "val x = 3")
        var res = repl.eval(2, "x*2")
        Assert.assertEquals(6, res.resultValue)
    }

    @Test
    fun TestDependsOnAnnotation(){
        repl.eval(1, "@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
    }

    @Test
    fun TestDependsOnAnnotations(){
        val sb = StringBuilder()
        sb.appendln("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
        sb.appendln("@file:Repository(\"https://repo.spring.io/libs-release\")")
        sb.appendln("@file:DependsOn(\"org.jetbrains.kotlinx:kotlinx.html.jvm:0.5.12\")")
        repl.eval(1, sb.toString())
    }
}