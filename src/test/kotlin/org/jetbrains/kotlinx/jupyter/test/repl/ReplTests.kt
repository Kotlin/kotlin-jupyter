package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainValue
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.maps.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.sequences.shouldBeEmpty
import io.kotest.matchers.sequences.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import jupyter.kotlin.JavaRuntime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.kotlinx.jupyter.OutputConfig
import org.jetbrains.kotlinx.jupyter.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.api.VariableStateImpl
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.generateDiagnostic
import org.jetbrains.kotlinx.jupyter.generateDiagnosticFromAbsolute
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlinx.jupyter.test.getOrFail
import org.jetbrains.kotlinx.jupyter.test.getStringValue
import org.jetbrains.kotlinx.jupyter.test.getValue
import org.jetbrains.kotlinx.jupyter.test.mapToStringValues
import org.jetbrains.kotlinx.jupyter.withPath
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.script.experimental.api.SourceCode

class ReplTests : AbstractSingleReplTest() {
    override val repl = makeSimpleRepl()

    @Test
    fun testRepl() {
        eval("val x = 3")
        val res = eval("x*2")
        res.resultValue shouldBe 6
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
                """.trimIndent()
            )

            print("")
        }
    }

    @Test
    fun testError() {
        val ex = shouldThrow<ReplCompilerException> {
            eval(
                """
                val foobar = 78
                val foobaz = "dsdsda"
                val ddd = ppp
                val ooo = foobar
                """.trimIndent()
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
            """.trimIndent()
        )

        res.resultValue shouldBe 1
    }

    @Test
    fun testDependsOnAnnotationCompletion() {
        eval(
            """
            @file:Repository("https://repo1.maven.org/maven2/")
            @file:DependsOn("com.github.doyaaaaaken:kotlin-csv-jvm:0.7.3")
            """.trimIndent()
        )

        val res = repl.completeBlocking("import com.github.", 18)
        res.shouldBeInstanceOf<CompletionResult.Success>()
        res.sortedMatches().contains("doyaaaaaken")
    }

    @Test
    fun testExternalStaticFunctions() {
        val res = eval(
            """
            @file:DependsOn("src/test/testData/kernelTestPackage-1.0.jar")
            import pack.*
            func()
            """.trimIndent()
        )

        res.resultValue shouldBe 42
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
            """.trimIndent()
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

        runBlocking {
            repl.complete("val t = foo", 11) {
                it.getOrFail().sortedMatches() shouldBe arrayListOf("foobar", "foobaz")
            }
        }
    }

    @Test
    fun testNoCompletionAfterNumbers() {
        runBlocking {
            repl.complete("val t = 42", 10) {
                it.getOrFail().sortedMatches().shouldBeEmpty()
            }
        }
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
            """.trimIndent()
        )
        runBlocking {
            repl.complete("df.filter { c_ }", 14) { result ->
                result.getOrFail().sortedMatches() shouldBe arrayListOf("c_meth_z(", "c_prop_x", "c_prop_y", "c_zzz")
            }
        }
    }

    @Test
    fun testParametersCompletion() {
        eval("fun f(xyz: Int) = xyz * 2")

        runBlocking {
            repl.complete("val t = f(x", 11) {
                it.getOrFail().sortedMatches() shouldBe arrayListOf("xyz = ")
            }
        }
    }

    @Test
    fun testDeprecationCompletion() {
        eval(
            """
            @Deprecated("use id() function instead")
            fun id_deprecated(x: Int) = x
            """.trimIndent()
        )

        runBlocking {
            repl.complete("val t = id_d", 12) { result ->
                result.getOrFail().sortedRaw().any {
                    it.text == "id_deprecated(" && it.deprecationLevel == DeprecationLevel.WARNING
                }.shouldBeTrue()
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
            """.trimIndent()
        )
        runBlocking {
            repl.listErrors(
                """
                val a = AClass("42", 3.14)
                val b: Int = "str"
                val c = foob
                """.trimIndent()
            ) { result ->
                val actualErrors = result.errors.toList()
                val path = actualErrors.first().sourcePath
                actualErrors shouldBe withPath(
                    path,
                    listOf(
                        generateDiagnostic(1, 16, 1, 20, "Type mismatch: inferred type is String but Int was expected", "ERROR"),
                        generateDiagnostic(1, 22, 1, 26, "The floating-point literal does not conform to the expected type String", "ERROR"),
                        generateDiagnostic(2, 14, 2, 19, "Type mismatch: inferred type is String but Int was expected", "ERROR"),
                        generateDiagnostic(3, 9, 3, 13, "Unresolved reference: foob", "ERROR")
                    )
                )
            }
        }
    }

    @Test
    fun testFreeCompilerArg() {
        runBlocking {
            val res = eval(
                """
                @file:CompilerArgs("-Xopt-in=kotlin.RequiresOptIn")
                """.trimIndent()
            )
            res.resultValue shouldBe Unit

            repl.listErrors(
                """
                import kotlin.time.*
                @OptIn(ExperimentalTime::class)
                val mark = TimeSource.Monotonic.markNow()
                """.trimIndent()
            ) { result ->
                result.errors.shouldBeEmpty()
            }
        }
    }

    @Test
    fun testErrorsListWithMagic() {
        runBlocking {
            repl.listErrors(
                """
                %use krangl
                
                val x = foobar
                3 * 14
                %trackClasspath
                """.trimIndent()
            ) { result ->
                val actualErrors = result.errors.toList()
                val path = actualErrors.first().sourcePath
                actualErrors shouldBe withPath(
                    path,
                    listOf(
                        generateDiagnostic(3, 9, 3, 15, "Unresolved reference: foobar", "ERROR")
                    )
                )
            }
        }
    }

    @Test
    fun testCompletionWithMagic() {
        eval("val foobar = 42")

        runBlocking {
            val code =
                """
                    
                %trackClasspath
            
                foo
                """.trimIndent()
            repl.complete(code, code.indexOf("foo") + 3) { result ->
                result.shouldBeInstanceOf<CompletionResult.Success>()
                result.sortedMatches() shouldBe arrayListOf("foobar")
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
        eval("1+1", null, 1)
        val res = eval("Out[1]")
        res.resultValue shouldBe 2
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
            captureOutput = false
        )

        eval("%output --max-buffer=42 --max-buffer-newline=33 --max-time=2000")
        repl.outputConfig shouldBe OutputConfig(
            cellOutputMaxSize = 100500,
            captureOutput = false,
            captureBufferMaxSize = 42,
            captureNewlineBufferSize = 33,
            captureBufferTimeLimitMs = 2000
        )

        eval("%output --reset-to-defaults")
        repl.outputConfig shouldBe OutputConfig()
    }

    @Test
    fun testJavaRuntimeUtils() {
        val result = eval("JavaRuntimeUtils.version")
        val resultVersion = result.resultValue
        val expectedVersion = JavaRuntime.version
        resultVersion shouldBe expectedVersion
    }

    @Test
    fun testKotlinMath() {
        val result = eval("2.0.pow(2.0)").resultValue
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
            """.trimIndent()
        ).resultValue

        res.shouldNotBeNull()
        res::class.qualifiedName shouldBe "org.RDKit.RWMol"
    }

    @Test
    fun testLambdaRendering() {
        val res = eval(
            """
            val foo: (Int) -> Int = {it + 1}
            foo
            """.trimIndent()
        ).resultValue
        @Suppress("UNCHECKED_CAST")
        (res as (Int) -> Int)(1) shouldBe 2
    }

    @Test
    fun testOutVarRendering() {
        eval("Out").resultValue.shouldNotBeNull()
    }
}

class ReplVarsTest : AbstractSingleReplTest() {
    override val repl = makeSimpleRepl()

    private val varState get() = repl.notebook.variablesState
    private val cellVars get() = repl.notebook.cellVariables

    private fun cellVarsAt(i: Int) = cellVars[i]!!
    private val firstCellVars get() = cellVarsAt(0)
    private val secondCellVars get() = cellVarsAt(1)

    @Test
    fun testVarsStateConsistency() {
        varState.shouldBeEmpty()
        eval(
            """
            val x = 1 
            val y = 0
            val z = 47
            """.trimIndent()
        )

        varState.mapToStringValues() shouldBe mutableMapOf(
            "x" to "1",
            "y" to "0",
            "z" to "47"
        )

        varState.getStringValue("x") shouldBe "1"
        varState.getStringValue("y") shouldBe "0"
        varState.getValue("z") shouldBe 47

        (varState["z"] as VariableStateImpl).update()
        repl.notebook.updateVariablesState(varState)
        varState.getValue("z") shouldBe 47
    }

    @Test
    fun testVarsEmptyState() {
        val res = eval("3+2")
        val strState = varState.mapToStringValues()
        varState.shouldBeEmpty()
        res.metadata.evaluatedVariablesState shouldBe strState
    }

    @Test
    fun testVarsCapture() {
        eval(
            """
            val x = 1 
            val y = "abc"
            val z = x
            """.trimIndent()
        )
        varState.mapToStringValues() shouldBe mapOf("x" to "1", "y" to "abc", "z" to "1")
        varState.getValue("x") shouldBe 1
        varState.getStringValue("y") shouldBe "abc"
        varState.getStringValue("z") shouldBe "1"
    }

    @Test
    fun testVarsCaptureSeparateCells() {
        eval(
            """
            val x = 1 
            val y = "abc"
            val z = x
            """.trimIndent()
        )
        varState.shouldNotBeEmpty()

        eval(
            """
            val x = "abc" 
            var y = 123
            val z = x
            """.trimIndent(),
            jupyterId = 1
        )
        varState shouldHaveSize 3
        varState.getStringValue("x") shouldBe "abc"
        varState.getValue("y") shouldBe 123
        varState.getStringValue("z") shouldBe "abc"

        eval(
            """
            val x = 1024 
            y += 123
            """.trimIndent(),
            jupyterId = 2
        )
        varState shouldHaveSize 3
        varState.getStringValue("x") shouldBe "1024"
        varState.getStringValue("y") shouldBe "${123 * 2}"
        varState.getValue("z") shouldBe "abc"
    }

    @Test
    fun testPrivateVarsCapture() {
        eval(
            """
            private val x = 1 
            private val y = "abc"
            val z = x
            """.trimIndent()
        )
        varState.mapToStringValues() shouldBe mapOf("x" to "1", "y" to "abc", "z" to "1")
        varState.getValue("x") shouldBe 1
    }

    @Test
    fun testPrivateVarsCaptureSeparateCells() {
        eval(
            """
            private val x = 1 
            private val y = "abc"
            private val z = x
            """.trimIndent()
        )
        varState.shouldNotBeEmpty()

        eval(
            """
            private val x = "abc" 
            var y = 123
            private val z = x
            """.trimIndent(),
            jupyterId = 1
        )
        varState shouldHaveSize 3
        varState.getStringValue("x") shouldBe "abc"
        varState.getValue("y") shouldBe 123
        varState.getStringValue("z") shouldBe "abc"

        eval(
            """
            private val x = 1024 
            y += x
            """.trimIndent(),
            jupyterId = 2
        )
        varState shouldHaveSize 3
        varState.getStringValue("x") shouldBe "1024"
        varState.getValue("y") shouldBe 123 + 1024
        varState.getStringValue("z") shouldBe "abc"
    }

    @Test
    fun testVarsUsageConsistency() {
        eval("3+2")
        cellVars shouldHaveSize 1
        cellVars.values.first().shouldBeEmpty()
    }

    @Test
    fun testVarsDefsUsage() {
        eval(
            """
            val x = 1
            val z = "abcd"
            var f = 47
            """.trimIndent()
        )
        cellVars shouldContainValue setOf("z", "f", "x")
    }

    @Test
    fun testVarsDefNRefUsage() {
        eval(
            """
            val x = "abcd"
            var f = 47
            """.trimIndent()
        )
        cellVars.shouldNotBeEmpty()

        eval(
            """
            val z = 1
            f += f
            """.trimIndent()
        )
        cellVars shouldContainValue setOf("z", "f", "x")
    }

    @Test
    fun testPrivateVarsDefNRefUsage() {
        eval(
            """
            val x = 124
            private var f = "abcd"
            """.trimIndent()
        )
        cellVars.shouldNotBeEmpty()

        eval(
            """
            private var z = 1
            z += x
            """.trimIndent()
        )
        cellVars shouldContainValue setOf("z", "f", "x")
    }

    @Test
    fun testSeparateDefsUsage() {
        eval(
            """
            val x = "abcd"
            var f = 47
            """.trimIndent(),
            jupyterId = 1
        )
        firstCellVars shouldContain "x"

        eval(
            """
            val x = 341
            var f = "abcd"
            """.trimIndent(),
            jupyterId = 2
        )
        cellVars.shouldNotBeEmpty()

        firstCellVars.shouldBeEmpty()
        secondCellVars shouldBe setOf("x", "f")
    }

    @Test
    fun testSeparatePrivateDefsUsage() {
        eval(
            """
            private val x = "abcd"
            private var f = 47
            """.trimIndent(),
            jupyterId = 1
        )
        firstCellVars shouldContain "x"

        eval(
            """
            val x = 341
            private var f = "abcd"
            """.trimIndent(),
            jupyterId = 2
        )
        cellVars.shouldNotBeEmpty()

        firstCellVars.shouldBeEmpty()
        secondCellVars shouldBe setOf("x", "f")
    }

    @Test
    fun testRecursiveVarsState() {
        eval(
            """
            val l = mutableListOf<Any>()
            l.add(listOf(l))
            
            val m = mapOf(1 to l)
            
            val z = setOf(1, 2, 4)
            """.trimIndent(),
            jupyterId = 1
        )
        varState.getStringValue("l") shouldBe "ArrayList: recursive structure"
        varState.getStringValue("m") shouldContain " recursive structure"
        varState.getStringValue("z") shouldBe "[1, 2, 4]"
    }

    @Test
    fun testSeparatePrivateCellsUsage() {
        eval(
            """
            private val x = "abcd"
            var f = 47
            internal val z = 47
            """.trimIndent(),
            jupyterId = 1
        )
        firstCellVars shouldContain "x"
        firstCellVars shouldContain "z"

        eval(
            """
            private val x = 341
            f += x
            protected val z = "abcd"
            """.trimIndent(),
            jupyterId = 2
        )
        cellVars.shouldNotBeEmpty()

        firstCellVars shouldBe setOf("f")
        secondCellVars shouldBe setOf("x", "f", "z")
    }

    @Test
    fun testVariableModification() {
        eval("var x = sqrt(25.0)", jupyterId = 1)
        varState.getStringValue("x") shouldBe "5.0"
        varState.getValue("x") shouldBe 5.0

        eval("x = x * x", jupyterId = 2)
        varState.getStringValue("x") shouldBe "25.0"
        varState.getValue("x") shouldBe 25.0
    }
}
