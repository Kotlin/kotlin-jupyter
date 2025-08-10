package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.inspectors.forAny
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.paths.shouldContainFile
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.sequences.shouldBeEmpty
import io.kotest.matchers.sequences.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import jupyter.kotlin.JavaRuntime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.kotlinx.jupyter.api.CodeCell
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.MimeTypes
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode.K1
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode.K2
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplUnwrappedExceptionImpl
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplLibraryException
import org.jetbrains.kotlinx.jupyter.generateDiagnostic
import org.jetbrains.kotlinx.jupyter.generateDiagnosticFromAbsolute
import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCount
import org.jetbrains.kotlinx.jupyter.messaging.toExecuteErrorReply
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlinx.jupyter.repl.OutputConfig
import org.jetbrains.kotlinx.jupyter.repl.result.EvalResultEx
import org.jetbrains.kotlinx.jupyter.test.getOrFail
import org.jetbrains.kotlinx.jupyter.test.renderedValue
import org.jetbrains.kotlinx.jupyter.test.withTempDirectories
import org.jetbrains.kotlinx.jupyter.util.DelegatingClassLoader
import org.jetbrains.kotlinx.jupyter.withPath
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE
import org.junit.jupiter.api.condition.OS
import java.io.File
import java.nio.file.Path
import kotlin.script.experimental.api.SourceCode

class ReplTests : AbstractSingleReplTest() {
    override val repl = makeSimpleRepl()

    @Test
    fun testRepl() {
        eval("val x = 3")
        val res = eval("x*2")
        res.renderedValue shouldBe 6
    }

    @Test
    fun testPropertiesGeneration() {
        // Note, this test should actually fail with ReplEvalRuntimeException, but 'cause of eval/compile
        // histories are out of sync, it fails with another exception. This test shows the wrong behavior and
        // should be fixed after fixing https://youtrack.jetbrains.com/issue/KT-36397

        // In fact, this shouldn't compile, but because of bug in compiler it fails in runtime
        evalError<ReplCompilerException>(
            """
            fun stack(vararg tup: Int): Int = tup.sum()
            val X = 1
            val x = stack(1, X)
            """.trimIndent(),
        )
    }

    @Test
    fun `compilation error`() {
        val ex =
            evalError<ReplCompilerException>(
                """
                val foobar = 78
                val foobaz = "dsdsda"
                val ddd = ppp
                val ooo = foobar
                """.trimIndent(),
            )

        val diag = ex.firstError
        val location = diag?.location
        val message = ex.message

        val expectedLocation = SourceCode.Location(SourceCode.Position(3, 11), SourceCode.Position(3, 14))
        val expectedMessage =
            when (repl.compilerMode) {
                K1 -> "at Cell In[-1], line 3, column 11: Unresolved reference: ppp"
                K2 -> "at Cell In[-1], line 3, column 11: Unresolved reference 'ppp'."
            }
        message shouldBe expectedMessage
        location shouldBe expectedLocation
    }

    @Test
    fun `runtime execution error`() {
        val ex =
            evalError<ReplEvalRuntimeException>(
                """
                try {
                    (null as String)
                } catch(e: NullPointerException) {
                    throw RuntimeException("XYZ", e)
                }
                """.trimIndent(),
            )
        when (repl.compilerMode) {
            K1 -> {
                with(ex.render()) {
                    shouldContain(NullPointerException::class.qualifiedName!!)
                    shouldContain("XYZ")
                    shouldContain("""at Line_\d+_jupyter.<init>\(Line_\d+\.jupyter.kts:2\)""".toRegex())
                    shouldNotContain(ReplEvalRuntimeException::class.simpleName!!)
                }
            }
            K2 -> {
                with(ex.render()) {
                    shouldContain(NullPointerException::class.qualifiedName!!)
                    shouldContain("XYZ")
                    shouldContain("""at Line_\d+_jupyter.${"\\$\\$"}eval\(Line_\d+\.jupyter.kts:4\)""".toRegex())
                    shouldNotContain(ReplEvalRuntimeException::class.simpleName!!)
                }
            }
        }
    }

    @Test
    fun testDependsOnAnnotation() {
        eval("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
    }

    @Test
    fun testImportResolutionAfterFailure() {
        val errorsRes = repl.listErrorsBlocking("import net.pearx.kasechange.*")
        when (repl.compilerMode) {
            K1 -> {
                errorsRes.errors shouldHaveSize 1

                val res =
                    eval(
                        """
                        @file:DependsOn("net.pearx.kasechange:kasechange-jvm:1.3.0")
                        import net.pearx.kasechange.*
                        1
                        """.trimIndent(),
                    )

                res.renderedValue shouldBe 1
            }
            K2 -> {
                // Wait for https://youtrack.jetbrains.com/issue/KTNB-916/K2-Repl-Add-support-for-Completion-and-Analysis
                errorsRes.errors.toList().shouldBeEmpty()
            }
        }
    }

    @Test
    fun testDependsOnAnnotationCompletion() {
        eval(
            """
            @file:Repository("https://repo1.maven.org/maven2/")
            @file:DependsOn("com.github.doyaaaaaken:kotlin-csv-jvm:0.7.3")
            """.trimIndent(),
        )

        val res = repl.completeBlocking("import com.github.", 18)
        res.shouldBeInstanceOf<CompletionResult.Success>()
        res.sortedMatches().contains("doyaaaaaken")
    }

    @Test
    fun testDependencyConfigurationAnnotationCompletion() {
        eval(
            """
            USE {
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation("io.github.config4k:config4k:0.4.2")
                }
            }
            """.trimIndent(),
        )

        val res = repl.completeBlocking("import io.github.", 17)
        res.shouldBeInstanceOf<CompletionResult.Success>()
        res.sortedMatches().contains("config4k")
    }

    @Test
    fun testExternalStaticFunctions() {
        val res =
            eval(
                """
                @file:DependsOn("src/test/testData/kernelTestPackage-1.0.jar")
                import pack.*
                func()
                """.trimIndent(),
            )

        res.renderedValue shouldBe 42
    }

    @Test
    fun testScriptIsolation() {
        evalError<ReplCompilerException>("org.jetbrains.kotlinx.jupyter.ReplLineMagics.use")
    }

    @Test
    fun testDependsOnAnnotations() {
        val res =
            eval(
                """
                @file:DependsOn("de.erichseifert.gral:gral-core:0.11")
                @file:Repository("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
                @file:DependsOn("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.2")
                """.trimIndent(),
            )

        val newClasspath = res.metadata.newClasspath
        newClasspath shouldHaveAtLeastSize 2

        val htmlLibPath = "org/jetbrains/kotlinx/kotlinx-html-jvm/0.7.2/kotlinx-html-jvm".replace('/', File.separatorChar)

        newClasspath.forAny { it shouldContain htmlLibPath }
    }

    @Test
    fun testCompletionSimple() {
        eval("val foobar = 42")
        eval("var foobaz = 43")

        val result = repl.completeBlocking("val t = foo", 11)
        when (repl.compilerMode) {
            K1 -> {
                result.getOrFail().sortedMatches() shouldBe arrayListOf("foobar", "foobaz")
            }
            K2 -> {
                // Wait for https://youtrack.jetbrains.com/issue/KTNB-916/K2-Repl-Add-support-for-Completion-and-Analysis
                result.getOrFail().sortedMatches().shouldBeEmpty()
            }
        }
    }

    @Test
    fun testNoCompletionAfterNumbers() {
        val result = repl.completeBlocking("val t = 42", 10)
        result.getOrFail().sortedMatches().shouldBeEmpty()
    }

    @Test
    fun testCompletionForImplicitReceivers() {
        eval(
            """
            class AClass(val c_prop_x: Int) {
                fun filter(xxx: (AClass).() -> Boolean): AClass {
                    return this
                }
            }
            val AClass.c_prop_y: Int
                get() = c_prop_x * c_prop_x
            
            fun AClass.c_meth_z(v: Int) = v * c_prop_y
            val df = AClass(10)
            val c_zzz = "some string"
            """.trimIndent(),
        )

        val result = repl.completeBlocking("df.filter { c_ }", 14)
        when (repl.compilerMode) {
            K1 -> {
                result.getOrFail().sortedMatches() shouldBe arrayListOf("c_meth_z(", "c_prop_x", "c_prop_y", "c_zzz")
            }
            K2 -> {
                // Wait for https://youtrack.jetbrains.com/issue/KTNB-916/K2-Repl-Add-support-for-Completion-and-Analysis
                result.getOrFail().sortedMatches().shouldBeEmpty()
            }
        }
    }

    @Test
    fun testParametersCompletion() {
        eval("fun f(xyz: Int) = xyz * 2")

        val result = repl.completeBlocking("val t = f(x", 11)
        when (repl.compilerMode) {
            K1 -> {
                result.getOrFail().sortedMatches() shouldBe arrayListOf("xyz = ")
            }
            K2 -> {
                // Wait for https://youtrack.jetbrains.com/issue/KTNB-916/K2-Repl-Add-support-for-Completion-and-Analysis
                result.getOrFail().sortedMatches() shouldBe arrayListOf()
            }
        }
    }

    @Test
    fun testDeprecationCompletion() {
        eval(
            """
            @Deprecated("use id() function instead")
            fun id_deprecated(x: Int) = x
            """.trimIndent(),
        )

        runBlocking {
            val result = repl.completeBlocking("val t = id_d", 12)
            when (repl.compilerMode) {
                K1 -> {
                    result
                        .getOrFail()
                        .sortedRaw()
                        .any {
                            it.text == "id_deprecated(" && it.deprecationLevel == DeprecationLevel.WARNING
                        }.shouldBeTrue()
                }
                K2 -> {
                    // Wait for https://youtrack.jetbrains.com/issue/KTNB-916/K2-Repl-Add-support-for-Completion-and-Analysis
                    result.getOrFail().sortedMatches() shouldBe arrayListOf()
                }
            }
        }
    }

    @Test
    fun testErrorsList() {
        eval(
            """
            data class AClass(val memx: Int, val memy: String)
            data class BClass(val memz: String, val mema: AClass)
            val foobar = 42
            var foobaz = "string"
            val v = BClass("KKK", AClass(5, "25"))
            """.trimIndent(),
        )

        val result =
            repl.listErrorsBlocking(
                """
                val a = AClass("42", 3.14)
                val b: Int = "str"
                val c = foob
                """.trimIndent(),
            )
        when (repl.compilerMode) {
            K1 -> {
                val actualErrors = result.errors.toList()
                val path = actualErrors.first().sourcePath
                actualErrors shouldBe
                    withPath(
                        path,
                        listOf(
                            generateDiagnostic(1, 16, 1, 20, "Type mismatch: inferred type is String but Int was expected", "ERROR"),
                            generateDiagnostic(
                                1,
                                22,
                                1,
                                26,
                                "The floating-point literal does not conform to the expected type String",
                                "ERROR",
                            ),
                            generateDiagnostic(2, 14, 2, 19, "Type mismatch: inferred type is String but Int was expected", "ERROR"),
                            generateDiagnostic(3, 9, 3, 13, "Unresolved reference: foob", "ERROR"),
                        ),
                    )
            }
            K2 -> {
                // Wait for https://youtrack.jetbrains.com/issue/KTNB-916/K2-Repl-Add-support-for-Completion-and-Analysis
                assert(result.errors.toList().isEmpty())
            }
        }
    }

    @Test
    fun testFreeCompilerArg() {
        val res =
            eval(
                """
                @file:CompilerArgs("-opt-in=kotlin.RequiresOptIn")
                """.trimIndent(),
            )
        res.renderedValue shouldBe Unit

        val actualErrors =
            repl
                .listErrorsBlocking(
                    """
                    import kotlin.time.*
                    @OptIn(ExperimentalTime::class)
                    val mark = TimeSource.Monotonic.markNow()
                    """.trimIndent(),
                ).errors
                .toList()

        actualErrors.shouldBeEmpty()
    }

    @Test
    fun testErrorsListWithMagic() {
        val result =
            repl.listErrorsBlocking(
                """
                %use dataframe
                
                val x = foobar
                3 * 14
                %trackClasspath
                """.trimIndent(),
            )
        when (repl.compilerMode) {
            K1 -> {
                val actualErrors = result.errors.toList()
                val path = actualErrors.first().sourcePath
                actualErrors shouldBe
                    withPath(
                        path,
                        listOf(
                            generateDiagnostic(3, 9, 3, 15, "Unresolved reference: foobar", "ERROR"),
                        ),
                    )
            }
            K2 -> {
                // Wait for https://youtrack.jetbrains.com/issue/KTNB-916/K2-Repl-Add-support-for-Completion-and-Analysis
                assert(result.errors.toList().isEmpty())
            }
        }
    }

    @Test
    fun testCompletionWithMagic() {
        eval("val foobar = 42")
        val code =
            """
                
            %trackClasspath
            
            foo
            """.trimIndent()

        val result = repl.completeBlocking(code, code.indexOf("foo") + 3)
        result.shouldBeInstanceOf<CompletionResult.Success>()
        when (repl.compilerMode) {
            K1 -> {
                result.sortedMatches() shouldBe arrayListOf("foobar")
            }
            K2 -> {
                // Wait for https://youtrack.jetbrains.com/issue/KTNB-916/K2-Repl-Add-support-for-Completion-and-Analysis
                result.sortedMatches().shouldBeEmpty()
            }
        }
    }

    @Test
    fun testCommands() {
        val code1 = ":help"
        val code2 = ":hex "

        runBlocking {
            repl.listErrors(code1) { result ->
                result.code shouldBe code1
                result.errors.shouldBeEmpty()
            }
            repl.listErrors(code2) { result ->
                result.code shouldBe code2
                val expectedList = listOf(generateDiagnosticFromAbsolute(code2, 0, 4, "Unknown command", "ERROR"))
                val actualList = result.errors.toList()
                actualList shouldBe expectedList
            }
            repl.complete(code2, 3) { result ->
                result.shouldBeInstanceOf<CompletionResult.Success>()
                result.sortedMatches() shouldBe listOf("help")
            }
        }
    }

    @Test
    fun testEmptyErrorsListJson() {
        val res = ListErrorsResult("someCode")
        Json.encodeToString(serializer(), res.message) shouldBe """{"code":"someCode","errors":[]}"""
    }

    @Test
    fun testOut() {
        eval("1+1", 1)
        val res = eval("Out[1]")
        res.renderedValue shouldBe 2
        evalError<ReplEvalRuntimeException>("Out[3]")
    }

    @Test
    fun testNoHistory() {
        eval("1+1", storeHistory = false)
        evalError<ReplEvalRuntimeException>("Out[1]")
    }

    @Test
    fun testOutputMagic() {
        val options = repl.options

        eval("%output --max-cell-size=100500 --no-stdout")
        options.outputConfig shouldBe
            OutputConfig(
                cellOutputMaxSize = 100500,
                captureOutput = false,
            )

        eval("%output --max-buffer=42 --max-buffer-newline=33 --max-time=2000")
        options.outputConfig shouldBe
            OutputConfig(
                cellOutputMaxSize = 100500,
                captureOutput = false,
                captureBufferMaxSize = 42,
                captureNewlineBufferSize = 33,
                captureBufferTimeLimitMs = 2000,
            )

        eval("%output --reset-to-defaults")
        options.outputConfig shouldBe OutputConfig()
    }

    @Test
    fun testJavaRuntimeUtils() {
        val result = eval("JavaRuntimeUtils.javaVersion.versionString")
        val resultVersion = result.renderedValue
        val expectedVersion = JavaRuntime.javaVersion.versionString
        resultVersion shouldBe expectedVersion
    }

    @Test
    fun testKotlinMath() {
        val result = eval("2.0.pow(2.0)").renderedValue
        result shouldBe 4.0
    }

    @Test
    @DisabledOnOs(OS.MAC)
    @EnabledForJreRange(max = JRE.JAVA_11)
    fun testNativeLibrary() {
        val libName = "GraphMolWrap"
        val testDataPath = "src/test/testData/nativeTest"
        val jarPath = "$testDataPath/org.RDKit.jar"

        val res =
            eval(
                """
                @file:DependsOn("$jarPath")
                import org.RDKit.RWMol
                import org.RDKit.RWMol.MolFromSmiles
                Native.loadLibrary(RWMol::class, "$libName", "$testDataPath")
                MolFromSmiles("c1ccccc1")
                """.trimIndent(),
            ).renderedValue

        res.shouldNotBeNull()
        res::class.qualifiedName shouldBe "org.RDKit.RWMol"
    }

    @Test
    fun testLambdaRendering() {
        val res =
            eval(
                """
                val foo: (Int) -> Int = {it + 1}
                foo
                """.trimIndent(),
            ).renderedValue
        @Suppress("UNCHECKED_CAST")
        (res as (Int) -> Int)(1) shouldBe 2
    }

    @Test
    fun testAnonymousObjectRendering() {
        eval("42")
        eval("val sim = object : ArrayList<String>() {}")
        val res = eval("sim").renderedValue
        res.toString() shouldBe "[]"
    }

    @Test
    fun testAnonymousObjectCustomRendering() {
        eval("USE { render<ArrayList<*>> { it.size } }")
        eval(
            """
            val sim = object : ArrayList<String>() {}
            sim.add("42")
            """.trimIndent(),
        )
        val res = eval("sim").renderedValue
        res shouldBe 1
    }

    @Test
    fun testValueClassRendering() {
        val result =
            eval(
                """
                class Obj(val x: Int)

                @JvmInline
                value class Wrapper(val o: Obj)
                """.trimIndent(),
            )
        when (repl.compilerMode) {
            K1 -> {
                eval(
                    """
                    USE {
                        addRenderer(
                            createRendererByCompileTimeType<Wrapper> { (it.value as Obj).x * 2 }
                        )
                    }
                    """.trimIndent(),
                )
                val res = eval("Wrapper(Obj(2))").renderedValue
                res shouldBe 2 * 2
            }
            K2 -> {
                // Wait for https://youtrack.jetbrains.com/issue/KT-74786/K2-Repl-Unexpected-returnTypeRef-when-defining-a-value-class
                result.shouldBeInstanceOf<EvalResultEx.Error>()
            }
        }
    }

    @Test
    fun testParametrizedClassRendering() {
        val setup =
            eval(
                """
                USE {
                    addRenderer(
                        createRendererByCompileTimeType<List<Int>> { (it.value as List<Int>).map { x -> x * 2 } }
                    )
                }
                """.trimIndent(),
            )
        setup.shouldBeInstanceOf<EvalResultEx.Success>()

        when (repl.compilerMode) {
            K1 -> {
                val res1 = eval("listOf(1, 2)").renderedValue
                res1 shouldBe listOf(2, 4)
            }
            K2 -> {
                // Wait for https://youtrack.jetbrains.com/issue/KT-75580/K2-Repl-Cannot-access-snippet-properties-using-Kotlin-reflection
                // We cannot correctly read the property types which breaks looking up registered compile time renders.
                // It's still falling because we don't have KTypes for result values
                val res1 = eval("listOf(1, 2)").renderedValue
                res1 shouldBe listOf(1, 2)
            }
        }

        val res2 = eval("listOf('1', '2')").renderedValue
        res2 shouldBe listOf('1', '2')
    }

    @Test
    fun testStdlibJdkExtensionsUsage() {
        eval("USE_STDLIB_EXTENSIONS()")
        val res =
            eval(
                """
                import kotlin.io.path.*
                import java.nio.file.Paths
                
                Paths.get(".").absolute()
                """.trimIndent(),
            ).renderedValue
        res.shouldBeInstanceOf<Path>()
    }

    @Test
    fun testArraysRendering() {
        eval("intArrayOf(1, 2, 3)").renderedValue.toString() shouldBe "[1, 2, 3]"
        eval("arrayOf(1 to 2, 3 to 4)").renderedValue.toString() shouldBe "[(1, 2), (3, 4)]"
        eval("booleanArrayOf(true, false)").renderedValue.toString() shouldBe "[true, false]"
    }

    @Test
    fun testOutVarRendering() {
        eval("Out").renderedValue.shouldNotBeNull()
    }

    @Test
    fun testMagicsErrorsReporting() {
        "%us".let { code ->
            listErrors(code).errors.toList() shouldBe listOf(generateDiagnosticFromAbsolute(code, 0, 3, "Unknown magic", "ERROR"))
        }

        "%use kmath".let { code ->
            listErrors(code).errors.toList().shouldBeEmpty()
        }
    }

    @Test
    fun testIssue356() {
        val result =
            eval(
                """
                sealed class BaseObjClass
                object Obj : BaseObjClass()
                val topLevelSequence = sequence {
                   yield(Obj)
                }
                open class Base {
                    val iter = topLevelSequence.iterator()
                }
                class Child: Base()
                
                Child::class.simpleName
                """.trimIndent(),
            )
        result.renderedValue shouldBe "Child"
    }

    @Test
    fun testIssue360() {
        eval("val a = 1")
        eval("fun b() = a")
        eval("b()").renderedValue shouldBe 1
    }

    @Test
    fun testRegexBug413() {
        val code =
            """
            Regex("(?<x>[0-9]*)").matchEntire("123456789")?.groups?.get("x")?.value
            """.trimIndent()

        val res1 = eval(code)
        res1.renderedValue shouldBe "123456789"
        val res2 = eval(code)
        res2.renderedValue shouldBe "123456789"
    }

    @Test
    fun testRendererRecursion() {
        eval(
            """
            class A(val map: Map<*, *>)
            
            USE {
                render<A> {
                    textResult(it.map.toString())
                }
                render<Map<*, *>> {
                    A(it)
                }
            }
            """.trimIndent(),
        )
        eval("mapOf<String, String>()").renderedValue.shouldBeInstanceOf<MimeTypedResult> {
            it shouldContainKey MimeTypes.PLAIN_TEXT
            it[MimeTypes.PLAIN_TEXT] shouldBe "{}"
        }
    }

    @Test
    fun `unwrapped exceptions thrown in the wild or in library code shouldn't be wrapped, others should be wrapped`() {
        val wrappedExceptionClassName = ReplException::class.qualifiedName!!
        val unwrappedExceptionClassName = ReplUnwrappedExceptionImpl::class.qualifiedName!!
        val message = "sorry I was thrown"

        eval(
            """
            throw $unwrappedExceptionClassName("$message")
            """.trimIndent(),
        ).let { result ->
            val exception =
                result
                    .shouldBeInstanceOf<EvalResultEx.Error>()
                    .error
                    .shouldBeInstanceOf<ReplEvalRuntimeException>()
            val errorReply = exception.toExecuteErrorReply(ExecutionCount(1))
            errorReply.name shouldBe unwrappedExceptionClassName
            errorReply.value shouldBe message
            errorReply.traceback.shouldHaveSingleElement(message)
        }

        eval(
            """
            USE {
                onLoaded {
                    throw $unwrappedExceptionClassName("$message")
                }
            }
            """.trimIndent(),
        ).let { result ->
            val exception =
                result
                    .shouldBeInstanceOf<EvalResultEx.Error>()
                    .error
                    .shouldBeInstanceOf<ReplUnwrappedExceptionImpl>()
            val errorReply = exception.toExecuteErrorReply(ExecutionCount(1))
            errorReply.name shouldBe unwrappedExceptionClassName
            errorReply.value shouldBe message
            errorReply.traceback.shouldHaveSingleElement(message)
        }

        eval(
            """
            USE {
                onLoaded {
                    throw $wrappedExceptionClassName("$message")
                }
            }
            """.trimIndent(),
        ).let { result ->
            val exception =
                result
                    .shouldBeInstanceOf<EvalResultEx.Error>()
                    .error
                    .shouldBeInstanceOf<ReplLibraryException>()
            val errorReply = exception.toExecuteErrorReply(ExecutionCount(1))
            errorReply.name shouldBe ReplLibraryException::class.qualifiedName
            errorReply.value shouldBe "The problem is found in one of the loaded libraries: check library init codes"
            errorReply.traceback.shouldHaveAtLeastSize(5)
        }
    }

    @Test
    fun `intermediate classloader is available via notebook API`() {
        val res = eval("notebook.intermediateClassLoader")
        val intermediateClassLoader = res.renderedValue.shouldBeInstanceOf<DelegatingClassLoader>()
        val cellClass = intermediateClassLoader.loadClass("org.jetbrains.kotlinx.jupyter.api.CodeCell")
        cellClass shouldBe CodeCell::class.java
    }

    @Test
    @Disabled("Reproduces KTNB-709")
    fun `check that root package is imported correctly`() {
        // Commenting this execution makes test pass
        eval("notebook")
        eval(
            """
            @file:DependsOn("com.sealwu:kscript-tools:1.0.22")
            """.trimIndent(),
        )
        val result =
            eval(
                """
                evalBash("foo")
                """.trimIndent(),
            )

        result.shouldBeInstanceOf<EvalResultEx.Success>()
    }

    @Test
    fun testDelegate() {
        eval(
            """
            import kotlin.reflect.KProperty
            class CustomDelegate {
                private var value: String = "Default"

                operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
                    return value
                }

                operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: String) {
                    value = newValue
                }
            }
            val str by CustomDelegate()
            str
            """.trimIndent(),
        )
        val res =
            eval(
                """
            val str by CustomDelegate()
            str
        """,
            )
        res.renderedValue shouldBe "Default"
    }

    @Test
    fun testImport() {
        eval("import kotlin.random.*")
        val res = eval("Random.nextInt()")
        println(res.renderedValue)
    }

    @Test
    fun testReceiverObjects() {
        eval(
            """
            class ReceiverObject { fun helloFromReceiver() = println("Hello from receiver object") }
            class TestObject() { fun checkReceiver(block: ReceiverObject.() -> Unit) { block(ReceiverObject()) } }
            """.trimIndent(),
        )
        eval(
            """
            val obj = TestObject()
            obj.checkReceiver { helloFromReceiver() }
            """.trimIndent(),
        )
    }

    // Test for https://youtrack.jetbrains.com/issue/KT-75946/K2-Repl-Using-a-type-alias-as-property-type-crashes-the-compiler
    @Test
    fun testTypeAlias() {
        eval("typealias MyStr = String")
        eval("val x: MyStr = \"abc\"")
        val res = eval("x")
        when (repl.compilerMode) {
            K1 -> {
                res.renderedValue shouldBe "abc"
            }
            K2 -> {
                // Waiting for https://youtrack.jetbrains.com/issue/KT-75946/K2-Repl-Using-a-type-alias-as-property-type-crashes-the-compiler
                res.shouldBeInstanceOf<EvalResultEx.Error>()
            }
        }
    }

    @Test
    fun testCompanionObjects() {
        val res0 =
            eval(
                """
                class Foo(val foo: String) {
                    companion object {
                        fun hello(): String = "Hello"
                    }
                }
                """.trimIndent(),
            )
        res0.shouldBeInstanceOf<EvalResultEx.Success>()
        val res1 =
            eval(
                """
                fun Foo.Companion.wave() = hello()
                """.trimIndent(),
            )
        res1.shouldBeInstanceOf<EvalResultEx.Success>()
        val res2 =
            eval(
                """
                Foo.wave()
                """.trimIndent(),
            )
        res2.renderedValue shouldBe "Hello"
    }

    // Test for https://youtrack.jetbrains.com/issue/KT-75947/K2-Repl-Destructing-crashes-the-compiler
    @Test
    fun testDestructuring() {
        val res =
            eval(
                """
                val (name, age) = Pair("John", 42)
                name + age
                """.trimIndent(),
            )
        when (repl.compilerMode) {
            K1 -> {
                res.renderedValue shouldBe "John42"
            }
            K2 -> {
                res.shouldBeInstanceOf<EvalResultEx.Error>()
            }
        }
    }

    // Test for https://youtrack.jetbrains.com/issue/KTNB-965
    @Test
    fun testSmartCastKT965() {
        if (repl.compilerMode != K2) return
        val result =
            eval(
                """
                    data class SimilarClusters(
                        val clusters: List<String>
                    )

                    fun test(similarClusters: SimilarClusters?) {
                        when {
                            !similarClusters?.clusters.isNullOrEmpty() -> {
                                similarClusters.clusters
                            }
                        }
                    }
                    
                    test(SimilarClusters(listOf("a", "b")))
                "
                """.trimIndent(),
            )
        result.shouldBeInstanceOf<EvalResultEx.Success>()
    }

    // Test for https://youtrack.jetbrains.com/issue/KTNB-967
    @Test
    fun testGenericIntersectionInType() {
        if (repl.compilerMode != K2) return
        val result =
            eval(
                """
                    public fun <T : Comparable<T & Any>?> tempNotebook(list: List<T>): T = TODO()
                "
                """.trimIndent(),
            )
        // Waiting for https://youtrack.jetbrains.com/issue/KTNB-967 to be fixed
        // result.shouldBeInstanceOf<EvalResultEx.Success>()
        result.shouldBeInstanceOf<EvalResultEx.Error>()
    }

    // Test for https://youtrack.jetbrains.com/issue/KT-77202/K2-Repl-Local-Extension-Properties-are-not-supported
    @Test
    fun testExtensionProperties() {
        val res =
            eval(
                """
                class Foo() { }
                val Foo.bar
                    get() = "Hello"
                Foo().bar
                """.trimIndent(),
            )
        when (repl.compilerMode) {
            K1 -> res.renderedValue shouldBe "Hello"
            K2 -> res.shouldBeInstanceOf<EvalResultEx.Error>()
        }
    }

    // Test for https://youtrack.jetbrains.com/issue/KT-77470/K2-Repl-Lazy-Properties-crash-code-generation
    @Test
    fun testLazyProperties() {
        val res =
            eval(
                """
                val foo by lazy { 42 }
                foo
                """.trimIndent(),
            )
        res.renderedValue shouldBe 42
    }

    // Test for https://youtrack.jetbrains.com/projects/KT/issues/KT-78755/K2-Repl-Redeclaring-variables-does-not-work
    @Test
    fun `properties redeclaration should work`() {
        eval("val a = 1")
        eval("a").renderedValue shouldBe 1
        eval("val a = 2")
        eval("a").renderedValue shouldBe 2
    }

    // Test for https://youtrack.jetbrains.com/projects/KT/issues/KT-78755/K2-Repl-Redeclaring-variables-does-not-work
    @Test
    fun `functions redeclaration should work`() {
        eval("fun f() = 1")
        eval("f()").renderedValue shouldBe 1
        eval("fun f() = 'c'")
        eval("f()").renderedValue shouldBe 'c'
    }

    // KTNB-1129
    @Test
    fun `custom packages should be possible`() {
        eval(
            """
            SessionOptions.serializeScriptData = true
            """.trimIndent(),
        )

        // Doesn't work in K2, see KT-80019
        val res =
            eval(
                """
                package com.xxx.pack
                fun great() = "Great result!"
                """.trimIndent(),
            )

        eval("great()").let { invocationResult ->
            when (repl.compilerMode) {
                K1 -> invocationResult.renderedValue shouldBe "Great result!"
                K2 -> invocationResult.shouldBeInstanceOf<EvalResultEx.Error>()
            }
        }

        when (repl.compilerMode) {
            K1 -> {
                withTempDirectories("customPackages") {
                    val scriptsDir = newTempDir()
                    val sourcesDir = newTempDir()

                    val classNames =
                        CompiledScriptsSerializer().deserializeAndSave(
                            res.metadata.compiledData,
                            scriptsDir,
                            sourcesDir,
                        )

                    scriptsDir.resolve("com/xxx/pack/Line_1_jupyter.class").shouldExist()
                    sourcesDir.shouldContainFile("Line_1.kts")

                    classNames shouldBe listOf("com.xxx.pack.Line_1_jupyter")
                }
            }
            K2 -> {
                res.shouldBeInstanceOf<EvalResultEx.Error>()
                with(res.metadata.compiledData) {
                    scripts.shouldBeEmpty()
                    sources.shouldBeEmpty()
                }
            }
        }
    }
}
