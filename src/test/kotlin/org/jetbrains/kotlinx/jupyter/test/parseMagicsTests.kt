package org.jetbrains.kotlinx.jupyter.test

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.compiler.util.CodeInterval
import org.jetbrains.kotlinx.jupyter.compiler.util.SourceCodeImpl
import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.libraries.AbstractLibraryResolutionInfo
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessorImpl
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.libraries.getDefinitions
import org.jetbrains.kotlinx.jupyter.libraries.parseReferenceWithArgs
import org.jetbrains.kotlinx.jupyter.magics.FullMagicsHandler
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.NoopMagicsHandler
import org.jetbrains.kotlinx.jupyter.repl.ExecutedCodeLogging
import org.jetbrains.kotlinx.jupyter.repl.OutputConfig
import org.jetbrains.kotlinx.jupyter.repl.ReplOptions
import org.jetbrains.kotlinx.jupyter.util.toSourceCodePositionWithNewAbsolute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private typealias MagicsAndCodeIntervals = Pair<List<CodeInterval>, List<CodeInterval>>

class ParseArgumentsTests {

    @Test
    fun test1() {
        val (ref, args) = parseReferenceWithArgs(" lib ")
        assertEquals("lib", ref.name)
        assertEquals(0, args.count())
    }

    @Test
    fun test2() {
        val (ref, args) = parseReferenceWithArgs("lib(arg1)")
        assertEquals("lib", ref.name)
        assertEquals(1, args.count())
        assertEquals("arg1", args[0].value)
        assertEquals("", args[0].name)
    }

    @Test
    fun test3() {
        val (ref, args) = parseReferenceWithArgs("lib (arg1 = 1.2, arg2 = val2)")
        assertEquals("lib", ref.name)
        assertEquals(2, args.count())
        assertEquals("arg1", args[0].name)
        assertEquals("1.2", args[0].value)
        assertEquals("arg2", args[1].name)
        assertEquals("val2", args[1].value)
    }

    @Test
    fun test4() {
        val (ref, args) = parseReferenceWithArgs("""lets-plot(api="[1.0,)")""")
        assertEquals("lets-plot", ref.name)
        assertEquals(1, args.count())
        assertEquals("api", args[0].name)
        assertEquals("[1.0,)", args[0].value)
    }

    @Test
    fun test5() {
        val (ref, args) = parseReferenceWithArgs("""lets-plot(api = "[1.0,)"   , lib=1.5.3 )""")
        assertEquals("lets-plot", ref.name)
        assertEquals(2, args.count())
        assertEquals("api", args[0].name)
        assertEquals("[1.0,)", args[0].value)
        assertEquals("lib", args[1].name)
        assertEquals("1.5.3", args[1].value)
    }

    @Test
    fun test6() {
        val (ref, args) = parseReferenceWithArgs("""spark(my.api.version = 1.0   , lib-x=foo )""")
        assertEquals("spark", ref.name)
        assertEquals(2, args.count())
        assertEquals("my.api.version", args[0].name)
        assertEquals("1.0", args[0].value)
        assertEquals("lib-x", args[1].name)
        assertEquals("foo", args[1].value)
    }

    @Test
    fun testInfo1() {
        val requestUrl = "https://raw.githubusercontent.com/Kotlin/kotlin-jupyter/master/libraries/default.json"
        val (ref, args) = parseReferenceWithArgs("lib_name@url[$requestUrl]")
        assertEquals("lib_name", ref.name)

        val info = ref.info
        assertTrue(info is AbstractLibraryResolutionInfo.ByURL)
        assertEquals(requestUrl, info.url.toString())
        assertEquals(0, args.size)
    }

    @Test
    fun testInfo2() {
        val file = File("libraries/default.json").toString()
        val (ref, args) = parseReferenceWithArgs("@file[$file](param=val)")
        assertEquals("", ref.name)

        val info = ref.info
        assertTrue(info is AbstractLibraryResolutionInfo.ByFile)
        assertEquals(file, info.file.toString())
        assertEquals(1, args.size)
        assertEquals("param", args[0].name)
        assertEquals("val", args[0].value)
    }

    @Test
    fun testInfo3() {
        val (ref, args) = parseReferenceWithArgs("krangl@ref[0.8.2.5]")
        assertEquals("krangl", ref.name)

        val info = ref.info
        assertTrue(info is AbstractLibraryResolutionInfo.ByGitRef)
        assertEquals(40, info.sha.length, "Expected commit SHA, but was `${info.sha}`")
        assertEquals(0, args.size)
    }

    @Test
    fun testInfo4() {
        val (ref, _) = parseReferenceWithArgs("krangl@0.8.2.5")
        assertEquals("krangl", ref.name)
        assertTrue(ref.info is AbstractLibraryResolutionInfo.Default)
    }
}

class ParseMagicsTests {

    private class TestReplOptions : ReplOptions {
        override var trackClasspath = false
        override var executedCodeLogging = ExecutedCodeLogging.OFF
        override var writeCompiledClasses = false
        override var outputConfig = OutputConfig()
    }

    private val options = TestReplOptions()

    private fun test(code: String, expectedProcessedCode: String, librariesChecker: (List<LibraryDefinition>) -> Unit = {}) {
        val switcher = ResolutionInfoSwitcher.noop(EmptyResolutionInfoProvider)
        val magicsHandler = FullMagicsHandler(
            options,
            LibrariesProcessorImpl(testLibraryResolver, defaultRuntimeProperties.version),
            switcher,
        )
        val processor = MagicsProcessor(magicsHandler)
        with(processor.processMagics(code, tryIgnoreErrors = true)) {
            assertEquals(expectedProcessedCode, this.code)
            librariesChecker(libraries.getDefinitions(NotebookMock))
        }
    }

    private fun intervals(code: String, parseOutCellMarker: Boolean): MagicsAndCodeIntervals {
        val processor = MagicsProcessor(NoopMagicsHandler, parseOutCellMarker)

        val magicsIntervals = processor.magicsIntervals(code)
        val codeIntervals = processor.codeIntervals(code, magicsIntervals)

        return magicsIntervals.toList() to codeIntervals.toList()
    }

    private fun getParsedText(code: String, intervals: List<CodeInterval>): String {
        return intervals.joinToString("") {
            code.substring(it.from, it.to)
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
                    
            """.trimIndent(),
        ) { libs ->
            assertEquals(2, libs.size)
        }

        assertTrue(options.trackClasspath)

        test(
            """
                %trackClasspath off
            """.trimIndent(),
            "",
        )

        assertFalse(options.trackClasspath)
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
                    %trackExecution generated
            """.trimIndent(),
            """
                    
                    
                    val x = 9
                    
                    fun f() = 42
                    
            """.trimIndent(),
        ) { libs ->
            assertEquals(1, libs.size)
        }

        assertEquals(ExecutedCodeLogging.GENERATED, options.executedCodeLogging)
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

    @Test
    fun `cell marker is recognised if specified`() {
        val (magicsIntervals, codeIntervals) = intervals(
            """
            #%% some cell description
            
            fun f() = "pay respect"
            % some magic
            
            %some another magic
            """.trimIndent(),
            true,
        )

        assertEquals(3, magicsIntervals.size)
        assertEquals(2, codeIntervals.size)
    }

    @Test
    fun `cell marker in the middle is not recognised`() {
        val text = """
            #%% some cell description
            
            fun f() = "pay respect"
            % some magic
            #%% some another description - not recognised
            val x = 42
            %some another magic
        """.trimIndent()

        val (magicsIntervals, codeIntervals) = intervals(text, true)

        assertEquals(3, magicsIntervals.size)
        assertEquals(2, codeIntervals.size)

        val parsedMagics = getParsedText(text, magicsIntervals)
        assertEquals(
            """
            #%% some cell description
            % some magic
            %some another magic
            """.trimIndent(),
            parsedMagics,
        )
    }

    @Test
    fun `cell marker is not recognised if not specified`() {
        val (magicsIntervals, codeIntervals) = intervals(
            """
            #%% some cell description
            
            fun f() = "pay respect"
            % some magic
            
            %some another magic
            """.trimIndent(),
            false,
        )

        assertEquals(2, magicsIntervals.size)
        assertEquals(2, codeIntervals.size)
    }

    @Test
    fun `percent sign alone is parsed well`() {
        val (magicsIntervals, codeIntervals) = intervals(
            "%",
            false,
        )

        assertEquals(1, magicsIntervals.size)
        assertEquals(0, codeIntervals.size)
    }
}
