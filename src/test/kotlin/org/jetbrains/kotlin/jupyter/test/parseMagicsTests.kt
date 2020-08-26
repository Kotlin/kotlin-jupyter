package org.jetbrains.kotlin.jupyter.test

import org.jetbrains.kotlin.jupyter.ExecutedCodeLogging
import org.jetbrains.kotlin.jupyter.LibrariesDir
import org.jetbrains.kotlin.jupyter.LibraryDefinition
import org.jetbrains.kotlin.jupyter.MagicsProcessor
import org.jetbrains.kotlin.jupyter.OutputConfig
import org.jetbrains.kotlin.jupyter.ReplOptions
import org.jetbrains.kotlin.jupyter.defaultRuntimeProperties
import org.jetbrains.kotlin.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlin.jupyter.libraries.LibraryFactory
import org.jetbrains.kotlin.jupyter.libraries.LibraryResolutionInfo
import org.jetbrains.kotlin.jupyter.repl.SourceCodeImpl
import org.jetbrains.kotlin.jupyter.toSourceCodePositionWithNewAbsolute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class ParseArgumentsTests {
    private val libraryFactory = LibraryFactory.EMPTY

    @Test
    fun test1() {
        val (ref, args) = libraryFactory.parseReferenceWithArgs(" lib ")
        assertEquals("lib", ref.name)
        assertEquals(0, args.count())
    }

    @Test
    fun test2() {
        val (ref, args) = libraryFactory.parseReferenceWithArgs("lib(arg1)")
        assertEquals("lib", ref.name)
        assertEquals(1, args.count())
        assertEquals("arg1", args[0].value)
        assertEquals("", args[0].name)
    }

    @Test
    fun test3() {
        val (ref, args) = libraryFactory.parseReferenceWithArgs("lib (arg1 = 1.2, arg2 = val2)")
        assertEquals("lib", ref.name)
        assertEquals(2, args.count())
        assertEquals("arg1", args[0].name)
        assertEquals("1.2", args[0].value)
        assertEquals("arg2", args[1].name)
        assertEquals("val2", args[1].value)
    }

    @Test
    fun test4() {
        val (ref, args) = libraryFactory.parseReferenceWithArgs("""lets-plot(api="[1.0,)")""")
        assertEquals("lets-plot", ref.name)
        assertEquals(1, args.count())
        assertEquals("api", args[0].name)
        assertEquals("[1.0,)", args[0].value)
    }

    @Test
    fun test5() {
        val (ref, args) = libraryFactory.parseReferenceWithArgs("""lets-plot(api = "[1.0,)"   , lib=1.5.3 )""")
        assertEquals("lets-plot", ref.name)
        assertEquals(2, args.count())
        assertEquals("api", args[0].name)
        assertEquals("[1.0,)", args[0].value)
        assertEquals("lib", args[1].name)
        assertEquals("1.5.3", args[1].value)
    }

    @Test
    fun testInfo1() {
        val requestUrl = "https://raw.githubusercontent.com/Kotlin/kotlin-jupyter/master/libraries/default.json"
        val (ref, args) = libraryFactory.parseReferenceWithArgs("lib_name@url[$requestUrl]")
        assertEquals("lib_name", ref.name)

        val info = ref.info
        assertTrue(info is LibraryResolutionInfo.ByURL)
        assertEquals(requestUrl, info.url.toString())
        assertEquals(0, args.size)
    }

    @Test
    fun testInfo2() {
        val file = File("libraries/default.json").toString()
        val (ref, args) = libraryFactory.parseReferenceWithArgs("@file[$file](param=val)")
        assertEquals("", ref.name)

        val info = ref.info
        assertTrue(info is LibraryResolutionInfo.ByFile)
        assertEquals(file, info.file.toString())
        assertEquals(1, args.size)
        assertEquals("param", args[0].name)
        assertEquals("val", args[0].value)
    }

    @Test
    fun testInfo3() {
        val (ref, args) = libraryFactory.parseReferenceWithArgs("krangl@0.8.2.5")
        assertEquals("krangl", ref.name)

        val info = ref.info
        assertTrue(info is LibraryResolutionInfo.ByGitRef)
        assertEquals(40, info.sha.length, "Expected commit SHA, but was `${info.sha}`")
        assertEquals(0, args.size)
    }
}

class ParseMagicsTests {

    private class TestReplOptions : ReplOptions {
        override val currentBranch: String
            get() = standardResolverBranch
        override val librariesDir = File(LibrariesDir)
        override var trackClasspath = false
        override var executedCodeLogging = ExecutedCodeLogging.Off
        override var writeCompiledClasses = false
        override var outputConfig = OutputConfig()
    }

    private val options = TestReplOptions()

    private fun test(code: String, expectedProcessedCode: String, librariesChecker: (List<LibraryDefinition>) -> Unit = {}) {
        val libraryFactory = LibraryFactory.EMPTY
        val processor = MagicsProcessor(options, LibrariesProcessor(libraryFactory.testResolverConfig.libraries, defaultRuntimeProperties, libraryFactory))
        with(processor.processMagics(code, tryIgnoreErrors = true)) {
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
        test("\n%use krangl\n\n", "\n\n\n") { libs ->
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
        ) { libs ->
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
        ) { libs ->
            assertEquals(1, libs.size)
        }

        assertEquals(ExecutedCodeLogging.Generated, options.executedCodeLogging)
    }

    @Test
    fun `source location is correctly transformed`() {
        val sourceText =
            """
            fun g() = 99
            %use lets-plot
            %use wrongLib
            val x = 9
            """.trimIndent()

        val resultText =
            """
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
