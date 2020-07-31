package org.jetbrains.kotlin.jupyter.test

import org.jetbrains.kotlin.jupyter.ExecutedCodeLogging
import org.jetbrains.kotlin.jupyter.LibrariesProcessor
import org.jetbrains.kotlin.jupyter.LibraryDefinition
import org.jetbrains.kotlin.jupyter.MagicsProcessor
import org.jetbrains.kotlin.jupyter.OutputConfig
import org.jetbrains.kotlin.jupyter.ReplOptions
import org.jetbrains.kotlin.jupyter.parseLibraryName
import org.jetbrains.kotlin.jupyter.repl.completion.SourceCodeImpl
import org.jetbrains.kotlin.jupyter.toSourceCodePositionWithNewAbsolute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

class ParseMagicsTests {

    private class TestReplOptions : ReplOptions {
        override var trackClasspath = false
        override var executedCodeLogging = ExecutedCodeLogging.Off
        override var writeCompiledClasses = false
        override var outputConfig = OutputConfig()
    }

    private val options = TestReplOptions()

    private fun test(code: String, expectedProcessedCode: String, librariesChecker: (List<LibraryDefinition>) -> Unit = {}) {
        val processor = MagicsProcessor(options, LibrariesProcessor(testResolverConfig.libraries))
        with(processor.processMagics(code, true)) {
            assertEquals(expectedProcessedCode, this.code)
            librariesChecker(libraries)
        }
    }

    @Test
    fun `single magic`() {
        test("%use krangl", "") { libs ->
            assertEquals(1, libs.size)
        }
    }

    @Test
    fun `trailing newlines should be left`() {
        test("\n%use krangl\n\n", "\n\n\n"){ libs ->
            assertEquals(1, libs.size)
        }
    }

    @Test
    fun `multiple magics`() {
        test(
                """
                    %use lets-plot, krangl
                    
                    fun f() = 42
                    %trackClasspath
                    val x = 9
                    
                """.trimIndent(),
                """
                    
                    
                    fun f() = 42
                    
                    val x = 9
                    
                """.trimIndent()
        ){ libs ->
            assertEquals(2, libs.size)
        }

        assertTrue(options.trackClasspath)
    }

    @Test
    fun `wrong magics should be tolerated`() {
        test(
                """
                    %use lets-plot
                    %use wrongLib
                    val x = 9
                    %wrongMagic
                    fun f() = 42
                    %trackExecution -generated
                """.trimIndent(),
                """
                    
                    
                    val x = 9
                    
                    fun f() = 42
                    
                """.trimIndent()
        ){ libs ->
            assertEquals(1, libs.size)
        }

        assertEquals(ExecutedCodeLogging.Generated, options.executedCodeLogging)
    }

    @Test
    fun `source location is correctly transformed`() {
        val sourceText = """
            fun g() = 99
            %use lets-plot
            %use wrongLib
            val x = 9
        """.trimIndent()

        val resultText = """
            fun g() = 99
            
            
            val x = 9
        """.trimIndent()

        test(sourceText, resultText) { libs ->
            assertEquals(1, libs.size)
        }

        val source = SourceCodeImpl(1, sourceText)
        val result = SourceCodeImpl(1, resultText)
        val cursor = sourceText.indexOf("lets-plot")

        val actualPos = cursor.toSourceCodePositionWithNewAbsolute(source, result)
        assertNull(actualPos)
    }
}
