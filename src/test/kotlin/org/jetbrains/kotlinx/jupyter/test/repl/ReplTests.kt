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
import io.kotest.matchers.types.shouldBeInstanceOf
import jupyter.kotlin.JavaRuntime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.kotlinx.jupyter.OutputConfig
import org.jetbrains.kotlinx.jupyter.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.generateDiagnostic
import org.jetbrains.kotlinx.jupyter.generateDiagnosticFromAbsolute
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlinx.jupyter.test.getOrFail
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
            """.trimIndent()
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
            """.trimIndent()
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
            """.trimIndent()
        )

        val result = repl.listErrorsBlocking(
            """
            val a = AClass("42", 3.14)
            val b: Int = "str"
            val c = foob
            """.trimIndent()
        )
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

    @Test
    fun testFreeCompilerArg() {
        val res = eval(
            """
            @file:CompilerArgs("-Xopt-in=kotlin.RequiresOptIn")
            """.trimIndent()
        )
        res.resultValue shouldBe Unit

        repl.listErrorsBlocking(
            """
            import kotlin.time.*
            @OptIn(ExperimentalTime::class)
            val mark = TimeSource.Monotonic.markNow()
            """.trimIndent()
        ).errors.shouldBeEmpty()
    }

    @Test
    fun testErrorsListWithMagic() {
        val result = repl.listErrorsBlocking(
            """
            %use krangl
            
            val x = foobar
            3 * 14
            %trackClasspath
            """.trimIndent()
        )
        val actualErrors = result.errors.toList()
        val path = actualErrors.first().sourcePath
        actualErrors shouldBe withPath(
            path,
            listOf(
                generateDiagnostic(3, 9, 3, 15, "Unresolved reference: foobar", "ERROR")
            )
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
        val res = eval("Out").resultValue
        assertNotNull(res)
    }
}

class ReplVarsTest : AbstractSingleReplTest() {
    override val repl = makeSimpleRepl()

    @Test
    fun testVarsStateConsistency() {
        assertTrue(repl.notebook.variablesState.isEmpty())
        eval(
            """
            val x = 1 
            val y = 0
            val z = 47
            """.trimIndent()
        )

        val varsUpdate = mutableMapOf(
            "x" to "1",
            "y" to "0",
            "z" to "47"
        )
        assertEquals(varsUpdate, repl.notebook.variablesState.mapToStringValues())
        assertFalse(repl.notebook.variablesState.isEmpty())
        val varsState = repl.notebook.variablesState
        assertEquals("1", varsState.getStringValue("x"))
        assertEquals("0", varsState.getStringValue("y"))
        assertEquals(47, varsState.getValue("z"))

        (varsState["z"]!! as VariableStateImpl).update()
        repl.notebook.updateVariablesState(varsState)
        assertEquals(47, varsState.getValue("z"))
    }

    @Test
    fun testVarsEmptyState() {
        val res = eval("3+2")
        val state = repl.notebook.variablesState
        val strState = mutableMapOf<String, String>()
        state.forEach {
            strState[it.key] = it.value.stringValue ?: return@forEach
        }
        assertTrue(state.isEmpty())
        assertEquals(res.metadata.evaluatedVariablesState, strState)
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
        val varsState = repl.notebook.variablesState
        assertTrue(varsState.isNotEmpty())
        val strState = varsState.mapToStringValues()

        val goalState = mapOf("x" to "1", "y" to "abc", "z" to "1")
        assertEquals(strState, goalState)
        assertEquals(1, varsState.getValue("x"))
        assertEquals("abc", varsState.getStringValue("y"))
        assertEquals("1", varsState.getStringValue("z"))
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
        val varsState = repl.notebook.variablesState
        assertTrue(varsState.isNotEmpty())
        eval(
            """
            val x = "abc" 
            var y = 123
            val z = x
            """.trimIndent(),
            jupyterId = 1
        )
        assertTrue(varsState.isNotEmpty())
        assertEquals(3, varsState.size)
        assertEquals("abc", varsState.getStringValue("x"))
        assertEquals(123, varsState.getValue("y"))
        assertEquals("abc", varsState.getStringValue("z"))

        eval(
            """
            val x = 1024 
            y += 123
            """.trimIndent(),
            jupyterId = 2
        )
        val toStringValues = varsState.mapToStringValues()
        assertTrue(varsState.isNotEmpty())
        assertEquals(3, toStringValues.size)
        assertEquals("1024", toStringValues["x"])
        assertEquals("${123 * 2}", toStringValues["y"])
        assertEquals("abc", toStringValues["z"])
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
        val varsState = repl.notebook.variablesState
        assertTrue(varsState.isNotEmpty())
        val strState = varsState.mapValues { it.value.stringValue }

        val goalState = mapOf("x" to "1", "y" to "abc", "z" to "1")
        assertEquals(strState, goalState)
        assertEquals(1, varsState.getValue("x"))
        assertEquals("abc", varsState.getStringValue("y"))
        assertEquals("1", varsState.getStringValue("z"))
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
        val varsState = repl.notebook.variablesState
        assertTrue(varsState.isNotEmpty())
        eval(
            """
            private val x = "abc" 
            var y = 123
            private val z = x
            """.trimIndent(),
            jupyterId = 1
        )
        assertTrue(varsState.isNotEmpty())
        assertEquals(3, varsState.size)
        assertEquals("abc", varsState.getStringValue("x"))
        assertEquals(123, varsState.getValue("y"))
        assertEquals("abc", varsState.getStringValue("z"))

        eval(
            """
            private val x = 1024 
            y += x
            """.trimIndent(),
            jupyterId = 2
        )

        assertTrue(varsState.isNotEmpty())
        assertEquals(3, varsState.size)
        assertEquals("1024", varsState.getStringValue("x"))
        assertEquals(123 + 1024, varsState.getValue("y"))
        assertEquals("abc", varsState.getStringValue("z"))
    }

    @Test
    fun testVarsUsageConsistency() {
        eval("3+2")
        val state = repl.notebook.cellVariables
        assertTrue(state.values.size == 1)
        assertTrue(state.values.first().isEmpty())
        val setOfNextCell = setOf<String>()
        assertEquals(state.values.first(), setOfNextCell)
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
        val state = repl.notebook.cellVariables
        assertTrue(state.isNotEmpty())
        assertTrue(state.values.first().isNotEmpty())
        val setOfCell = setOf("z", "f", "x")
        assertTrue(state.containsValue(setOfCell))
    }

    @Test
    fun testVarsDefNRefUsage() {
        eval(
            """
            val x = "abcd"
            var f = 47
            """.trimIndent()
        )
        val state = repl.notebook.cellVariables
        assertTrue(state.isNotEmpty())
        eval(
            """
            val z = 1
            f += f
            """.trimIndent()
        )
        assertTrue(state.isNotEmpty())

        val setOfCell = setOf("z", "f", "x")
        assertTrue(state.containsValue(setOfCell))
    }

    @Test
    fun testPrivateVarsDefNRefUsage() {
        eval(
            """
            val x = 124
            private var f = "abcd"
            """.trimIndent(),
            jupyterId = 1
        )
        var state = repl.notebook.cellVariables
        assertTrue(state.isNotEmpty())

        // f is not accessible from here
        eval(
            """
            private var z = 1
            z += x
            """.trimIndent(),
            jupyterId = 2
        )
        state = repl.notebook.cellVariables
        assertTrue(state.isNotEmpty())
        // ignore primitive references precise check for Java > 8
        val setOfCell = setOf("z", "x")
        assertTrue(state.containsValue(setOfCell))
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
        val state = repl.notebook.cellVariables
        assertTrue(state[0]!!.contains("x"))

        eval(
            """
            val x = 341
            var f = "abcd"
            """.trimIndent(),
            jupyterId = 2
        )
        assertTrue(state.isNotEmpty())
        assertTrue(state[0]!!.isEmpty())
        assertTrue(state[1]!!.contains("x"))

        val setOfPrevCell = setOf<String>()
        val setOfNextCell = setOf("x", "f")
        assertEquals(state[0], setOfPrevCell)
        assertEquals(state[1], setOfNextCell)
    }

    @Test
    fun testAnonymousObjectRendering() {
        eval("42")
        eval("val sim = object : ArrayList<String>() {}")
        val res = eval("sim").resultValue
        res.toString() shouldBe "[]"
    }

    @Test
    fun testAnonymousObjectCustomRendering() {
        eval("USE { render<ArrayList<*>> { it.size } }")
        eval(
            """
            val sim = object : ArrayList<String>() {}
            sim.add("42")
            """.trimIndent()
        )
        val res = eval("sim").resultValue
        res shouldBe 1
    }

    @Test
    fun testOutVarRendering() {
        eval("Out").resultValue.shouldNotBeNull()
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
        val state = repl.notebook.cellVariables
        assertTrue(state[0]!!.contains("x"))

        eval(
            """
            val x = 341
            private var f = "abcd"
            """.trimIndent(),
            jupyterId = 2
        )
        assertTrue(state.isNotEmpty())
        assertTrue(state[0]!!.isEmpty())
        assertTrue(state[1]!!.contains("x"))

        val setOfPrevCell = setOf<String>()
        val setOfNextCell = setOf("x", "f")
        assertEquals(state[0], setOfPrevCell)
        assertEquals(state[1], setOfNextCell)
    }

    @Test
    fun testRecursiveVarsState() {
        val res = eval(
            """
            val l = mutableListOf<Any>()
            l.add(listOf(l))
            
            val m = mapOf(1 to l)
            
            val z = setOf(1, 2, 4)
            """.trimIndent(),
            jupyterId = 1
        ).metadata
        val state = repl.notebook.variablesState
        assertTrue(state.contains("l"))
        assertTrue(state.contains("m"))
        assertTrue(state.contains("z"))

        assertEquals("ArrayList: recursive structure", state["l"]!!.stringValue)
        assertTrue(state["m"]!!.stringValue!!.contains(" recursive structure"))
        assertEquals("[1, 2, 4]", state["z"]!!.stringValue)

        val serializer = repl.variablesSerializer
        val descriptor = res.evaluatedVariablesState["l"]!!.fieldDescriptor
        val innerList = descriptor["elementData"]!!.fieldDescriptor["data"]
        val newData = serializer.doIncrementalSerialization(0, "l", "data", innerList!!)
        assertEquals(2, newData.fieldDescriptor.size)
    }

    @Test
    fun testUnchangedVars() {
        eval(
            """
            var l = 11111
            val m = "abc"
            """.trimIndent(),
            jupyterId = 1
        )
        var state = repl.notebook.unchangedVariables()
        val res = eval(
            """
            l += 11111
            """.trimIndent(),
            jupyterId = 2
        ).metadata.evaluatedVariablesState
        state = repl.notebook.unchangedVariables()
        assertEquals(1, state.size)
        assertTrue(state.contains("m"))
    }

    @Test
    fun testMutableList() {
        eval(
            """
            val l = mutableListOf(1, 2, 3, 4)
            """.trimIndent(),
            jupyterId = 1
        )
        val serializer = repl.variablesSerializer
        val res = eval(
            """
            l.add(5)
            """.trimIndent(),
            jupyterId = 2
        ).metadata.evaluatedVariablesState
        val innerList = res["l"]!!.fieldDescriptor["elementData"]!!.fieldDescriptor["data"]
        val newData = serializer.doIncrementalSerialization(0, "l", "data", innerList!!)
        assertTrue(newData.isContainer)
        assertTrue(newData.fieldDescriptor.size > 4)
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
        val state = repl.notebook.cellVariables
        assertTrue(state[0]!!.contains("x"))
        assertTrue(state[0]!!.contains("z"))

        eval(
            """
            private val x = 341
            f += x
            protected val z = "abcd"
            """.trimIndent(),
            jupyterId = 2
        )
        assertTrue(state.isNotEmpty())
        assertTrue(state[0]!!.isNotEmpty())
        assertFalse(state[0]!!.contains("x"))
        assertFalse(state[0]!!.contains("z"))
        assertTrue(state[1]!!.contains("x"))

        val setOfPrevCell = setOf("f")
        val setOfNextCell = setOf("x", "f", "z")
        assertEquals(state[0], setOfPrevCell)
        assertEquals(state[1], setOfNextCell)
    }

    @Test
    fun unchangedVariablesGapedRedefinition() {
        eval(
            """
            private val x = "abcd"
            var f = 47
            internal val z = 47
            """.trimIndent(),
            jupyterId = 1
        )
        var state = repl.notebook.unchangedVariables()
        assertEquals(3, state.size)

        eval(
            """
            private val x = "abcd"
            var f = 47 
            internal val z = 47
            """.trimIndent(),
            jupyterId = 2
        )
        state = repl.notebook.unchangedVariables()
        assertEquals(0, state.size)

        eval(
            """
            var f = 47 
            """.trimIndent(),
            jupyterId = 3
        )
        state = repl.notebook.unchangedVariables()
        assertEquals(1, state.size)
    }
}

class ReplVarsSerializationTest : AbstractSingleReplTest() {
    override val repl = makeSimpleRepl()

    @Test
    fun simpleContainerSerialization() {
        val res = eval(
            """
            val x = listOf(1, 2, 3, 4)
            var f = 47
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        assertEquals(2, varsData.size)
        assertTrue(varsData.containsKey("x"))
        assertTrue(varsData.containsKey("f"))

        val listData = varsData["x"]!!
        assertTrue(listData.isContainer)
        assertEquals(2, listData.fieldDescriptor.size)
        val listDescriptors = listData.fieldDescriptor

        assertEquals("4", listDescriptors["size"]!!.value)
        assertFalse(listDescriptors["size"]!!.isContainer)

        val actualContainer = listDescriptors.entries.first().value!!
        assertEquals(2, actualContainer.fieldDescriptor.size)
        assertTrue(actualContainer.isContainer)
        assertEquals(listOf(1, 2, 3, 4).toString().substring(1, actualContainer.value!!.length + 1), actualContainer.value)

        val serializer = repl.variablesSerializer
        val newData = serializer.doIncrementalSerialization(0, "x", "data", actualContainer)
    }

    @Test
    fun testUnchangedVarsRedefinition() {
        val res = eval(
            """
            val x = listOf(1, 2, 3, 4)
            var f = 47
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        assertEquals(2, varsData.size)
        assertTrue(varsData.containsKey("x"))
        assertTrue(varsData.containsKey("f"))
        var unchangedVariables = repl.notebook.unchangedVariables()
        assertTrue(unchangedVariables.isNotEmpty())

        eval(
            """
            val x = listOf(1, 2, 3, 4)
            """.trimIndent(),
            jupyterId = 1
        )
        unchangedVariables = repl.notebook.unchangedVariables()
        assertTrue(unchangedVariables.contains("x"))
        assertTrue(unchangedVariables.contains("f"))
    }

    @Test
    fun moreThanDefaultDepthContainerSerialization() {
        val res = eval(
            """
            val x = listOf(listOf(1), listOf(2), listOf(3), listOf(4))
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        assertEquals(1, varsData.size)
        assertTrue(varsData.containsKey("x"))

        val listData = varsData["x"]!!
        assertTrue(listData.isContainer)
        assertEquals(2, listData.fieldDescriptor.size)
        val listDescriptors = listData.fieldDescriptor

        assertEquals("4", listDescriptors["size"]!!.value)
        assertFalse(listDescriptors["size"]!!.isContainer)

        val actualContainer = listDescriptors.entries.first().value!!
        assertEquals(2, actualContainer.fieldDescriptor.size)
        assertTrue(actualContainer.isContainer)

        actualContainer.fieldDescriptor.forEach { (name, serializedState) ->
            if (name == "size") {
                assertEquals("4", serializedState!!.value)
            } else {
                assertEquals(0, serializedState!!.fieldDescriptor.size)
                assertTrue(serializedState.isContainer)
            }
        }
    }

    @Test
    fun cyclicReferenceTest() {
        val res = eval(
            """
            class C {
                inner class Inner;
                val i = Inner()
                val counter = 0
            }                
            val c = C()
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        assertEquals(1, varsData.size)
        assertTrue(varsData.containsKey("c"))

        val serializedState = varsData["c"]!!
        assertTrue(serializedState.isContainer)
        val descriptor = serializedState.fieldDescriptor
        assertEquals(2, descriptor.size)
        assertEquals("0", descriptor["counter"]!!.value)

        val serializer = repl.variablesSerializer

        val newData = serializer.doIncrementalSerialization(0, "c", "i", descriptor["i"]!!)
    }

    @Test
    fun incrementalUpdateTest() {
        val res = eval(
            """
            val x = listOf(listOf(1), listOf(2), listOf(3), listOf(4))
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        assertEquals(1, varsData.size)

        val listData = varsData["x"]!!
        assertTrue(listData.isContainer)
        assertEquals(2, listData.fieldDescriptor.size)
        val actualContainer = listData.fieldDescriptor.entries.first().value!!
        val serializer = repl.variablesSerializer

        val newData = serializer.doIncrementalSerialization(0, "x", listData.fieldDescriptor.entries.first().key, actualContainer)
        val receivedDescriptor = newData.fieldDescriptor
        assertEquals(4, receivedDescriptor.size)

        var values = 1
        receivedDescriptor.forEach { (_, state) ->
            val fieldDescriptor = state!!.fieldDescriptor
            assertEquals(0, fieldDescriptor.size)
            assertTrue(state.isContainer)
            assertEquals("${values++}", state.value)
        }

        val depthMostNode = actualContainer.fieldDescriptor.entries.first { it.value!!.isContainer }
        val serializationAns = serializer.doIncrementalSerialization(0, "x", depthMostNode.key, depthMostNode.value!!)
    }

    @Test
    fun incrementalUpdateTestWithPath() {
        val res = eval(
            """
            val x = listOf(listOf(1), listOf(2), listOf(3), listOf(4))
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        val listData = varsData["x"]!!
        assertEquals(2, listData.fieldDescriptor.size)
        val actualContainer = listData.fieldDescriptor.entries.first().value!!
        val serializer = repl.variablesSerializer
        val path = listOf("x", "a")

        val newData = serializer.doIncrementalSerialization(0, "x", listData.fieldDescriptor.entries.first().key, actualContainer, path)
        val receivedDescriptor = newData.fieldDescriptor
        assertEquals(4, receivedDescriptor.size)

        var values = 1
        receivedDescriptor.forEach { (_, state) ->
            val fieldDescriptor = state!!.fieldDescriptor
            assertEquals(0, fieldDescriptor.size)
            assertTrue(state.isContainer)
            assertEquals("${values++}", state.value)
        }
    }

    @Test
    fun testMapContainer() {
        val res = eval(
            """
            val x = mapOf(1 to "a", 2 to "b", 3 to "c", 4 to "c")
            val m = mapOf(1 to "a")
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        assertEquals(2, varsData.size)
        assertTrue(varsData.containsKey("x"))

        val mapData = varsData["x"]!!
        assertTrue(mapData.isContainer)
        assertEquals(6, mapData.fieldDescriptor.size)
        val listDescriptors = mapData.fieldDescriptor

        assertTrue(listDescriptors.containsKey("values"))
        assertTrue(listDescriptors.containsKey("entries"))
        assertTrue(listDescriptors.containsKey("keys"))

        val valuesDescriptor = listDescriptors["values"]!!
        assertEquals("4", valuesDescriptor.fieldDescriptor["size"]!!.value)
        assertTrue(valuesDescriptor.fieldDescriptor["data"]!!.isContainer)

        val serializer = repl.variablesSerializer

        var newData = serializer.doIncrementalSerialization(0, "x", "values", valuesDescriptor)
        var newDescriptor = newData.fieldDescriptor
        assertEquals("4", newDescriptor["size"]!!.value)
        assertEquals(3, newDescriptor["data"]!!.fieldDescriptor.size)
        val ansSet = mutableSetOf("a", "b", "c")
        newDescriptor["data"]!!.fieldDescriptor.forEach { (_, state) ->
            assertFalse(state!!.isContainer)
            assertTrue(ansSet.contains(state.value))
            ansSet.remove(state.value)
        }
        assertTrue(ansSet.isEmpty())

        val entriesDescriptor = listDescriptors["entries"]!!
        assertEquals("4", valuesDescriptor.fieldDescriptor["size"]!!.value)
        assertTrue(valuesDescriptor.fieldDescriptor["data"]!!.isContainer)
        newData = serializer.doIncrementalSerialization(0, "x", "entries", entriesDescriptor)
        newDescriptor = newData.fieldDescriptor
        assertEquals("4", newDescriptor["size"]!!.value)
        assertEquals(4, newDescriptor["data"]!!.fieldDescriptor.size)
        ansSet.add("1=a")
        ansSet.add("2=b")
        ansSet.add("3=c")
        ansSet.add("4=c")

        newDescriptor["data"]!!.fieldDescriptor.forEach { (_, state) ->
            assertFalse(state!!.isContainer)
            assertTrue(ansSet.contains(state.value))
            ansSet.remove(state.value)
        }
        assertTrue(ansSet.isEmpty())
    }

    @Test
    fun testSetContainer() {
        var res = eval(
            """
            val x = setOf("a", "b", "cc", "c")
            """.trimIndent(),
            jupyterId = 1
        )
        var varsData = res.metadata.evaluatedVariablesState
        assertEquals(1, varsData.size)
        assertTrue(varsData.containsKey("x"))

        var setData = varsData["x"]!!
        assertTrue(setData.isContainer)
        assertEquals(2, setData.fieldDescriptor.size)
        var setDescriptors = setData.fieldDescriptor
        assertEquals("4", setDescriptors["size"]!!.value)
        assertTrue(setDescriptors["data"]!!.isContainer)
        assertEquals(4, setDescriptors["data"]!!.fieldDescriptor.size)
        assertEquals("a", setDescriptors["data"]!!.fieldDescriptor["a"]!!.value)
        assertTrue(setDescriptors["data"]!!.fieldDescriptor.containsKey("b"))
        assertTrue(setDescriptors["data"]!!.fieldDescriptor.containsKey("cc"))
        assertTrue(setDescriptors["data"]!!.fieldDescriptor.containsKey("c"))

        res = eval(
            """
            val c = mutableSetOf("a", "b", "cc", "c")
            """.trimIndent(),
            jupyterId = 2
        )
        varsData = res.metadata.evaluatedVariablesState
        assertEquals(2, varsData.size)
        assertTrue(varsData.containsKey("c"))

        setData = varsData["c"]!!
        assertTrue(setData.isContainer)
        assertEquals(2, setData.fieldDescriptor.size)
        setDescriptors = setData.fieldDescriptor
        assertEquals("4", setDescriptors["size"]!!.value)
        assertTrue(setDescriptors["data"]!!.isContainer)
        assertEquals(4, setDescriptors["data"]!!.fieldDescriptor.size)
        assertEquals("a", setDescriptors["data"]!!.fieldDescriptor["a"]!!.value)
        assertTrue(setDescriptors["data"]!!.fieldDescriptor.containsKey("b"))
        assertTrue(setDescriptors["data"]!!.fieldDescriptor.containsKey("cc"))
        assertTrue(setDescriptors["data"]!!.fieldDescriptor.containsKey("c"))
    }

    @Test
    fun testSerializationMessage() {
        val res = eval(
            """
            val x = listOf(listOf(1), listOf(2), listOf(3), listOf(4))
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        assertEquals(1, varsData.size)
        val listData = varsData["x"]!!
        assertTrue(listData.isContainer)
        val actualContainer = listData.fieldDescriptor.entries.first().value!!
        val propertyName = listData.fieldDescriptor.entries.first().key

        runBlocking {
            repl.serializeVariables(1, "x", mapOf(propertyName to actualContainer)) { result ->
                val data = result.descriptorsState
                assertTrue(data.isNotEmpty())

                val innerList = data.entries.last().value
                assertTrue(innerList.isContainer)
                val receivedDescriptor = innerList.fieldDescriptor

                assertEquals(4, receivedDescriptor.size)
                var values = 1
                receivedDescriptor.forEach { (_, state) ->
                    val fieldDescriptor = state!!.fieldDescriptor
                    assertEquals(0, fieldDescriptor.size)
                    assertTrue(state.isContainer)
                    assertEquals("${values++}", state.value)
                }
            }
        }

        runBlocking {
            repl.serializeVariables("x", mapOf(propertyName to actualContainer)) { result ->
                val data = result.descriptorsState
                assertTrue(data.isNotEmpty())

                val innerList = data.entries.last().value
                assertTrue(innerList.isContainer)
                val receivedDescriptor = innerList.fieldDescriptor

                assertEquals(4, receivedDescriptor.size)
                var values = 1
                receivedDescriptor.forEach { (_, state) ->
                    val fieldDescriptor = state!!.fieldDescriptor
                    assertEquals(0, fieldDescriptor.size)
                    assertTrue(state.isContainer)
                    assertEquals("${values++}", state.value)
                }
            }
        }
    }

    @Test
    fun testCyclicSerializationMessage() {
        val res = eval(
            """
            class C {
                inner class Inner;
                val i = Inner()
                val counter = 0
            }
            val c = C()
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        assertEquals(1, varsData.size)
        val listData = varsData["c"]!!
        assertTrue(listData.isContainer)
        val actualContainer = listData.fieldDescriptor.entries.first().value!!
        val propertyName = listData.fieldDescriptor.entries.first().key

        runBlocking {
            repl.serializeVariables(1, "c", mapOf(propertyName to actualContainer)) { result ->
                val data = result.descriptorsState
                assertTrue(data.isNotEmpty())

                val innerList = data.entries.last().value
                assertTrue(innerList.isContainer)
                val receivedDescriptor = innerList.fieldDescriptor
                assertEquals(1, receivedDescriptor.size)
                val originalClass = receivedDescriptor.entries.first().value!!
                assertEquals(2, originalClass.fieldDescriptor.size)
                assertTrue(originalClass.fieldDescriptor.containsKey("i"))
                assertTrue(originalClass.fieldDescriptor.containsKey("counter"))

                val anotherI = originalClass.fieldDescriptor["i"]!!
                runBlocking {
                    repl.serializeVariables(1, "c", mapOf(propertyName to anotherI)) { res ->
                        val data = res.descriptorsState
                        val innerList = data.entries.last().value
                        assertTrue(innerList.isContainer)
                        val receivedDescriptor = innerList.fieldDescriptor
                        assertEquals(1, receivedDescriptor.size)
                        val originalClass = receivedDescriptor.entries.first().value!!
                        assertEquals(2, originalClass.fieldDescriptor.size)
                        assertTrue(originalClass.fieldDescriptor.containsKey("i"))
                        assertTrue(originalClass.fieldDescriptor.containsKey("counter"))
                    }
                }
            }
        }
    }

    @Test
    fun testUnchangedVariablesSameCell() {
        eval(
            """
            private val x = "abcd"
            var f = 47
            internal val z = 47
            """.trimIndent(),
            jupyterId = 1
        )
        val state = repl.notebook.unchangedVariables()
        val setOfCell = setOf("x", "f", "z")
        assertTrue(state.isNotEmpty())
        assertEquals(setOfCell, state)

        eval(
            """
            private val x = "44"
            var f = 47
            """.trimIndent(),
            jupyterId = 1
        )
        assertTrue(state.isNotEmpty())
        // it's ok that there's more info, cache's data would filter out
        assertEquals(setOf("f", "x", "z"), state)
    }

    @Test
    fun testUnchangedVariables() {
        eval(
            """
            private val x = "abcd"
            var f = 47
            internal val z = 47
            """.trimIndent(),
            jupyterId = 1
        )
        var state = repl.notebook.unchangedVariables()
        val setOfCell = setOf("x", "f", "z")
        assertTrue(state.isNotEmpty())
        assertEquals(setOfCell, state)

        eval(
            """
            private val x = 341
            f += x
            protected val z = "abcd"
            """.trimIndent(),
            jupyterId = 2
        )
        assertTrue(state.isEmpty())
        val setOfPrevCell = setOf("f")
        assertNotEquals(setOfCell, setOfPrevCell)

        eval(
            """
            private val x = 341
            protected val z = "abcd"
            """.trimIndent(),
            jupyterId = 3
        )
        state = repl.notebook.unchangedVariables()
//        assertTrue(state.isNotEmpty())
//        assertEquals(state, setOfPrevCell)

        eval(
            """
            private val x = "abcd"
            var f = 47
            internal val z = 47
            """.trimIndent(),
            jupyterId = 4
        )
        state = repl.notebook.unchangedVariables()
        assertTrue(state.isEmpty())
    }

    @Test
    fun testSerializationClearInfo() {
        var res = eval(
            """
            val x = listOf(1, 2, 3, 4)
            """.trimIndent(),
            jupyterId = 1
        ).metadata.evaluatedVariablesState
        var state = repl.notebook.unchangedVariables()
        res = eval(
            """
            val x = listOf(1, 2, 3, 4)
            """.trimIndent(),
            jupyterId = 2
        ).metadata.evaluatedVariablesState
    }
}
