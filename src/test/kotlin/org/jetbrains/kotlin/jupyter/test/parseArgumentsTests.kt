package org.jetbrains.kotlin.jupyter.test

import org.jetbrains.kotlin.jupyter.parseLibraryName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParseArgumentsTests {

    @Test
    fun test1() {
        val (name, args) = parseLibraryName(" lib ")
        assertEquals("lib", name)
        assertEquals(0, args.count())
    }

    @Test
    fun test2() {
        val (name, args) = parseLibraryName("lib(arg1)")
        assertEquals("lib", name)
        assertEquals(1, args.count())
        assertEquals("arg1", args[0].value)
        assertEquals("", args[0].name)
    }

    @Test
    fun test3() {
        val (name, args) = parseLibraryName("lib (arg1 = 1.2, arg2 = val2)")
        assertEquals("lib", name)
        assertEquals(2, args.count())
        assertEquals("arg1", args[0].name)
        assertEquals("1.2", args[0].value)
        assertEquals("arg2", args[1].name)
        assertEquals("val2", args[1].value)
    }
}