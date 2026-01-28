package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.compiler.util.CodeInterval
import org.jetbrains.kotlinx.jupyter.compiler.util.SourceCodeImpl
import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.libraries.AbstractLibraryResolutionInfo
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessorImpl
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.libraries.createLibraryHttpUtil
import org.jetbrains.kotlinx.jupyter.libraries.getDefinitions
import org.jetbrains.kotlinx.jupyter.logging.LogbackLoggingManager
import org.jetbrains.kotlinx.jupyter.magics.CompositeMagicsHandler
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.NoopMagicsHandler
import org.jetbrains.kotlinx.jupyter.magics.contexts.createDefaultMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.loadMagicHandlerFactories
import org.jetbrains.kotlinx.jupyter.repl.ExecutedCodeLogging
import org.jetbrains.kotlinx.jupyter.repl.OutputConfig
import org.jetbrains.kotlinx.jupyter.repl.ReplOptions
import org.jetbrains.kotlinx.jupyter.util.toSourceCodePositionWithNewAbsolute
import org.junit.jupiter.api.Test
import java.io.File

private typealias MagicsAndCodeIntervals = Pair<List<CodeInterval>, List<CodeInterval>>

class ParseArgumentsTests {
    private val httpUtil = createLibraryHttpUtil(testLoggerFactory)

    private fun parseReferenceWithArgs(ref: String) = httpUtil.libraryReferenceParser.parseReferenceWithArgs(ref)

    @Test
    fun test1() {
        val (ref, args) = parseReferenceWithArgs(" lib ")
        ref.name shouldBe "lib"
        args.count() shouldBe 0
    }

    @Test
    fun test2() {
        val (ref, args) = parseReferenceWithArgs("lib(arg1)")
        ref.name shouldBe "lib"
        args.count() shouldBe 1
        args[0].value shouldBe "arg1"
        args[0].name shouldBe ""
    }

    @Test
    fun test3() {
        val (ref, args) = parseReferenceWithArgs("lib (arg1 = 1.2, arg2 = val2)")
        ref.name shouldBe "lib"
        args.count() shouldBe 2
        args[0].name shouldBe "arg1"
        args[0].value shouldBe "1.2"
        args[1].name shouldBe "arg2"
        args[1].value shouldBe "val2"
    }

    @Test
    fun test4() {
        val (ref, args) = parseReferenceWithArgs("""lets-plot(api="[1.0,)")""")
        ref.name shouldBe "lets-plot"
        args.count() shouldBe 1
        args[0].name shouldBe "api"
        args[0].value shouldBe "[1.0,)"
    }

    @Test
    fun test5() {
        val (ref, args) = parseReferenceWithArgs("""lets-plot(api = "[1.0,)"   , lib=1.5.3 )""")
        ref.name shouldBe "lets-plot"
        args.count() shouldBe 2
        args[0].name shouldBe "api"
        args[0].value shouldBe "[1.0,)"
        args[1].name shouldBe "lib"
        args[1].value shouldBe "1.5.3"
    }

    @Test
    fun test6() {
        val (ref, args) = parseReferenceWithArgs("""spark(my.api.version = 1.0   , lib-x=foo )""")
        ref.name shouldBe "spark"
        args.count() shouldBe 2
        args[0].name shouldBe "my.api.version"
        args[0].value shouldBe "1.0"
        args[1].name shouldBe "lib-x"
        args[1].value shouldBe "foo"
    }

    @Test
    fun testInfo1() {
        val requestUrl = "https://raw.githubusercontent.com/Kotlin/kotlin-jupyter/master/libraries/default.json"
        val (ref, args) = parseReferenceWithArgs("lib_name@url[$requestUrl]")
        ref.name shouldBe "lib_name"

        val info = ref.info
        info.shouldBeTypeOf<AbstractLibraryResolutionInfo.ByURL>()
        info.url.toString() shouldBe requestUrl
        args.size shouldBe 0
    }

    @Test
    fun testInfo2() {
        val file = File("libraries/default.json").toString()
        val (ref, args) = parseReferenceWithArgs("@file[$file](param=val)")
        ref.name shouldBe ""
        val info = ref.info
        info.shouldBeTypeOf<AbstractLibraryResolutionInfo.ByFile>()
        info.file.toString() shouldBe file
        args.size shouldBe 1
        args[0].name shouldBe "param"
        args[0].value shouldBe "val"
    }

    @Test
    fun testInfo3() {
        val (ref, args) = parseReferenceWithArgs("krangl@ref[0.8.2.5]")
        ref.name shouldBe "krangl"

        val info = ref.info
        info.shouldBeTypeOf<AbstractLibraryResolutionInfo.ByGitRef>()
        info.sha.length shouldBe 40
        args.size shouldBe 0
    }

    @Test
    fun testInfo4() {
        val (ref, _) = parseReferenceWithArgs("krangl@0.8.2.5")
        ref.name shouldBe "krangl"
        ref.info.shouldBeTypeOf<AbstractLibraryResolutionInfo.Default>()
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

    private fun test(
        code: String,
        expectedProcessedCode: String,
        librariesChecker: (List<LibraryDefinition>) -> Unit = {},
    ) {
        val httpUtil = createLibraryHttpUtil(testLoggerFactory)
        val switcher = ResolutionInfoSwitcher.noop(EmptyResolutionInfoProvider(httpUtil.libraryInfoCache))
        val librariesProcessor =
            LibrariesProcessorImpl(
                testLibraryResolver,
                httpUtil.libraryReferenceParser,
                defaultRuntimeProperties.version,
            )
        val loggingManager = LogbackLoggingManager(testLoggerFactory)

        // Create a context with all the dependencies
        val context =
            createDefaultMagicHandlerContext(
                librariesProcessor,
                switcher,
                options,
                loggingManager,
            )

        // Create a registry and register all the handlers
        val magicsHandler =
            CompositeMagicsHandler(context).apply {
                createAndRegister(loadMagicHandlerFactories())
            }

        val processor = MagicsProcessor(magicsHandler)
        with(processor.processMagics(code, tryIgnoreErrors = true)) {
            this.code shouldBe expectedProcessedCode
            librariesChecker(libraries.getDefinitions(NotebookMock))
        }
    }

    private fun intervals(
        code: String,
        parseOutCellMarker: Boolean,
    ): MagicsAndCodeIntervals {
        val processor = MagicsProcessor(NoopMagicsHandler, parseOutCellMarker)

        val magicsIntervals = processor.magicsIntervals(code)
        val codeIntervals = processor.codeIntervals(code, magicsIntervals)

        return magicsIntervals.toList() to codeIntervals.toList()
    }

    private fun getParsedText(
        code: String,
        intervals: List<CodeInterval>,
    ): String =
        intervals.joinToString("") {
            code.substring(it.from, it.to)
        }

    @Test
    fun `single magic`() {
        test("%use dataframe", "") { libs ->
            libs.size shouldBe 1
        }
    }

    @Test
    fun `trailing newlines should be left`() {
        test("\n%use dataframe\n\n", "\n\n\n") { libs ->
            libs.size shouldBe 1
        }
    }

    @Test
    fun `multiple magics`() {
        test(
            """
            %use lets-plot, dataframe
            
            fun f() = 42
            %trackClasspath
            val x = 9
            
            """.trimIndent(),
            """
            
            
            fun f() = 42
            
            val x = 9
            
            """.trimIndent(),
        ) { libs ->
            libs.size shouldBe 2
        }

        options.trackClasspath.shouldBeTrue()

        test(
            """
            %trackClasspath off
            """.trimIndent(),
            "",
        )

        options.trackClasspath.shouldBeFalse()
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
            libs.size shouldBe 1
        }

        options.executedCodeLogging shouldBe ExecutedCodeLogging.GENERATED
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
            libs.size shouldBe 1
        }

        val source = SourceCodeImpl(1, sourceText)
        val result = SourceCodeImpl(1, resultText)
        val cursor = sourceText.indexOf("lets-plot")

        val actualPos = cursor.toSourceCodePositionWithNewAbsolute(source, result)
        actualPos.shouldBeNull()
    }

    @Test
    fun `cell marker is recognised if specified`() {
        val (magicsIntervals, codeIntervals) =
            intervals(
                """
                #%% some cell description
                
                fun f() = "pay respect"
                % some magic
                
                %some another magic
                """.trimIndent(),
                true,
            )

        magicsIntervals.size shouldBe 3
        codeIntervals.size shouldBe 2
    }

    @Test
    fun `cell marker in the middle is not recognised`() {
        val text =
            """
            #%% some cell description
            
            fun f() = "pay respect"
            % some magic
            #%% some another description - not recognised
            val x = 42
            %some another magic
            """.trimIndent()

        val (magicsIntervals, codeIntervals) = intervals(text, true)

        magicsIntervals.size shouldBe 3
        codeIntervals.size shouldBe 2

        val parsedMagics = getParsedText(text, magicsIntervals)
        parsedMagics shouldBe
            """
            #%% some cell description
            % some magic
            %some another magic
            """.trimIndent()
    }

    @Test
    fun `cell marker is not recognised if not specified`() {
        val (magicsIntervals, codeIntervals) =
            intervals(
                """
                #%% some cell description
                
                fun f() = "pay respect"
                % some magic
                
                %some another magic
                """.trimIndent(),
                false,
            )

        magicsIntervals.size shouldBe 2
        codeIntervals.size shouldBe 2
    }

    @Test
    fun `percent sign alone is parsed well`() {
        val (magicsIntervals, codeIntervals) =
            intervals(
                "%",
                false,
            )

        magicsIntervals.size shouldBe 1
        codeIntervals.size shouldBe 0
    }
}
