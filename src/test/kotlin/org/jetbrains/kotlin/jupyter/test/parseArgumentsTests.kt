package org.jetbrains.kotlin.jupyter.test

import org.jetbrains.kotlin.jupyter.parseLibraryName
import org.junit.Assert
import org.junit.Test

class ParseArgumentsTests {

    @Test
    fun test1() {
        val (name, args) = parseLibraryName(" lib ")
        Assert.assertEquals("lib", name)
        Assert.assertEquals(0, args.count())
    }

    @Test
    fun test2() {
        val (name, args) = parseLibraryName("lib(arg1)")
        Assert.assertEquals("lib", name)
        Assert.assertEquals(1, args.count())
        Assert.assertEquals("arg1", args[0].name)
        Assert.assertNull(args[0].value)
    }

    @Test
    fun test3() {
        val (name, args) = parseLibraryName("lib (arg1 = 1.2, arg2)")
        Assert.assertEquals("lib", name)
        Assert.assertEquals(2, args.count())
        Assert.assertEquals("arg1", args[0].name)
        Assert.assertEquals("1.2", args[0].value)
        Assert.assertEquals("arg2", args[1].name)
        Assert.assertNull(args[1].value)
    }
}