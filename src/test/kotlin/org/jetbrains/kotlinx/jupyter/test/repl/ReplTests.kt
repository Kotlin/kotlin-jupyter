package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldNotBeNull
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
import org.jetbrains.kotlinx.jupyter.OutputConfig
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.generateDiagnostic
import org.jetbrains.kotlinx.jupyter.generateDiagnosticFromAbsolute
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlinx.jupyter.test.getOrFail
import org.jetbrains.kotlinx.jupyter.withPath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
        shouldThrow<ReplCompilerException> {
            eval(
                """
                fun stack(vararg tup: Int): Int = tup.sum()
                val X = 1
                val x = stack(1, X)
                """.trimIndent(),
            )

            print("")
        }
    }

    @Test
    fun `compilation error`() {
        val ex = shouldThrow<ReplCompilerException> {
            eval(
                """
                val foobar = 78
                val foobaz = "dsdsda"
                val ddd = ppp
                val ooo = foobar
                """.trimIndent(),
            )
        }

        val diag = ex.firstError
        val location = diag?.location
        val message = ex.message

        val expectedLocation = SourceCode.Location(SourceCode.Position(3, 11), SourceCode.Position(3, 14))
        val expectedMessage = "Line_0.${repl.fileExtension} (3:11 - 14) Unresolved reference: ppp"

        location shouldBe expectedLocation
        message shouldBe expectedMessage
    }

    @Test
    fun `runtime execution error`() {
        val ex = shouldThrow<ReplEvalRuntimeException> {
            eval(
                """
                try {
                    (null as String)
                } catch(e: NullPointerException) {
                    throw RuntimeException("XYZ", e)
                }
                """.trimIndent(),
            )
        }
        with(ex.render()) {
            shouldContain(NullPointerException::class.qualifiedName!!)
            shouldContain("XYZ")
            shouldContain("""at Line_\d+_jupyter.<init>\(Line_\d+\.jupyter.kts:2\)""".toRegex())
            shouldNotContain(ReplEvalRuntimeException::class.simpleName!!)
        }
    }

    @Test
    fun testDependsOnAnnotation() {
        eval("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
    }

    @Test
    fun testImportResolutionAfterFailure() {
        val errorsRes = repl.listErrorsBlocking("import net.pearx.kasechange.*")
        errorsRes.errors shouldHaveSize 1

        val res = eval(
            """
            @file:DependsOn("net.pearx.kasechange:kasechange-jvm:1.3.0")
            import net.pearx.kasechange.*
            1
            """.trimIndent(),
        )

        res.renderedValue shouldBe 1
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
        val res = eval(
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
        shouldThrowAny {
            eval("org.jetbrains.kotlinx.jupyter.ReplLineMagics.use")
        }
    }

    @Test
    fun testDependsOnAnnotations() {
        val res = eval(
            """
            @file:DependsOn("de.erichseifert.gral:gral-core:0.11")
            @file:Repository("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.2")
            """.trimIndent(),
        )

        val newClasspath = res.metadata.newClasspath
        newClasspath shouldHaveAtLeastSize 2

        val htmlLibPath = "org/jetbrains/kotlinx/kotlinx-html-jvm/0.7.2/kotlinx-html-jvm".replace('/', File.separatorChar)

        newClasspath.any { htmlLibPath in it }.shouldBeTrue()
    }

    @Test
    fun testCompletionSimple() {
        eval("val foobar = 42")
        eval("var foobaz = 43")

        val result = repl.completeBlocking("val t = foo", 11)
        result.getOrFail().sortedMatches() shouldBe arrayListOf("foobar", "foobaz")
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
        result.getOrFail().sortedMatches() shouldBe arrayListOf("c_meth_z(", "c_prop_x", "c_prop_y", "c_zzz")
    }

    @Test
    fun testParametersCompletion() {
        eval("fun f(xyz: Int) = xyz * 2")

        val result = repl.completeBlocking("val t = f(x", 11)
        result.getOrFail().sortedMatches() shouldBe arrayListOf("xyz = ")
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
            result.getOrFail().sortedRaw().any {
                it.text == "id_deprecated(" && it.deprecationLevel == DeprecationLevel.WARNING
            }.shouldBeTrue()
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

        val result = repl.listErrorsBlocking(
            """
            val a = AClass("42", 3.14)
            val b: Int = "str"
            val c = foob
            """.trimIndent(),
        )
        val actualErrors = result.errors.toList()
        val path = actualErrors.first().sourcePath
        actualErrors shouldBe withPath(
            path,
            listOf(
                generateDiagnostic(1, 16, 1, 20, "Type mismatch: inferred type is String but Int was expected", "ERROR"),
                generateDiagnostic(1, 22, 1, 26, "The floating-point literal does not conform to the expected type String", "ERROR"),
                generateDiagnostic(2, 14, 2, 19, "Type mismatch: inferred type is String but Int was expected", "ERROR"),
                generateDiagnostic(3, 9, 3, 13, "Unresolved reference: foob", "ERROR"),
            ),
        )
    }

    @Test
    fun testFreeCompilerArg() {
        val res = eval(
            """
            @file:CompilerArgs("-opt-in=kotlin.RequiresOptIn")
            """.trimIndent(),
        )
        res.renderedValue shouldBe Unit

        val actualErrors = repl.listErrorsBlocking(
            """
            import kotlin.time.*
            @OptIn(ExperimentalTime::class)
            val mark = TimeSource.Monotonic.markNow()
            """.trimIndent(),
        ).errors.toList()

        actualErrors.shouldBeEmpty()
    }

    @Test
    fun testErrorsListWithMagic() {
        val result = repl.listErrorsBlocking(
            """
            %use krangl
            
            val x = foobar
            3 * 14
            %trackClasspath
            """.trimIndent(),
        )
        val actualErrors = result.errors.toList()
        val path = actualErrors.first().sourcePath
        actualErrors shouldBe withPath(
            path,
            listOf(
                generateDiagnostic(3, 9, 3, 15, "Unresolved reference: foobar", "ERROR"),
            ),
        )
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
        result.sortedMatches() shouldBe arrayListOf("foobar")
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
        shouldThrowAny { eval("Out[3]") }
    }

    @Test
    fun testNoHistory() {
        eval("1+1", storeHistory = false)
        shouldThrow<ReplEvalRuntimeException> {
            eval("Out[1]")
        }
    }

    @Test
    fun testOutputMagic() {
        eval("%output --max-cell-size=100500 --no-stdout")
        repl.outputConfig shouldBe OutputConfig(
            cellOutputMaxSize = 100500,
            captureOutput = false,
        )

        eval("%output --max-buffer=42 --max-buffer-newline=33 --max-time=2000")
        repl.outputConfig shouldBe OutputConfig(
            cellOutputMaxSize = 100500,
            captureOutput = false,
            captureBufferMaxSize = 42,
            captureNewlineBufferSize = 33,
            captureBufferTimeLimitMs = 2000,
        )

        eval("%output --reset-to-defaults")
        repl.outputConfig shouldBe OutputConfig()
    }

    @Test
    fun testJavaRuntimeUtils() {
        val result = eval("JavaRuntimeUtils.version")
        val resultVersion = result.renderedValue
        val expectedVersion = JavaRuntime.version
        resultVersion shouldBe expectedVersion
    }

    @Test
    fun testKotlinMath() {
        val result = eval("2.0.pow(2.0)").renderedValue
        result shouldBe 4.0
    }

    @Test
    fun testNativeLibrary() {
        val libName = "GraphMolWrap"
        val testDataPath = "src/test/testData/nativeTest"
        val jarPath = "$testDataPath/org.RDKit.jar"

        val res = eval(
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
        val res = eval(
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
        eval(
            """
            class Obj(val x: Int)

            @JvmInline
            value class Wrapper(val o: Obj)
            """.trimIndent(),
        )

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

    @Test
    fun testParametrizedClassRendering() {
        eval(
            """
            USE {
                addRenderer(
                    createRendererByCompileTimeType<List<Int>> { (it.value as List<Int>).map { x -> x * 2 } }
                )
            }
            """.trimIndent(),
        )

        val res1 = eval("listOf(1, 2)").renderedValue
        res1 shouldBe listOf(2, 4)

        val res2 = eval("listOf('1', '2')").renderedValue
        res2 shouldBe listOf('1', '2')
    }

    @Test
    fun testStdlibJdkExtensionsUsage() {
        eval("USE_STDLIB_EXTENSIONS()")
        val res = eval(
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
        ).renderedValue shouldBe "Child"
    }

    @Test
    fun testIssue360() {
        eval("val a = 1")
        eval("fun b() = a")
        eval("b()").renderedValue shouldBe 1
    }

    @Test
    fun testRegexBug413() {
        val code = """
            Regex("(?<x>[0-9]*)").matchEntire("123456789")?.groups?.get("x")?.value
        """.trimIndent()

        eval(code)
        assertThrows<ReplEvalRuntimeException> {
            eval(code)
        }
    }
}
