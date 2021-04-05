package org.jetbrains.kotlinx.jupyter.test.repl

import jupyter.kotlin.JavaRuntime
import jupyter.kotlin.receivers.ConstReceiver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.OutputConfig
import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.generateDiagnostic
import org.jetbrains.kotlinx.jupyter.generateDiagnosticFromAbsolute
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlinx.jupyter.test.classpath
import org.jetbrains.kotlinx.jupyter.withPath
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.script.experimental.api.SourceCode
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.fail

class ReplTests : AbstractReplTest() {
    private val repl = ReplForJupyterImpl(resolutionInfoProvider, classpath)

    @Test
    fun testRepl() {
        repl.eval("val x = 3")
        val res = repl.eval("x*2")
        assertEquals(6, res.resultValue)
    }

    @Test
    fun testPropertiesGeneration() {
        // Note, this test should actually fail with ReplEvalRuntimeException, but 'cause of eval/compile
        // histories are out of sync, it fails with another exception. This test shows the wrong behavior and
        // should be fixed after fixing https://youtrack.jetbrains.com/issue/KT-36397

        // In fact, this shouldn't compile, but because of bug in compiler it fails in runtime
        assertThrows<ReplCompilerException> {
            repl.eval(
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
        try {
            repl.eval(
                """
                val foobar = 78
                val foobaz = "dsdsda"
                val ddd = ppp
                val ooo = foobar
                """.trimIndent()
            )
        } catch (ex: ReplCompilerException) {
            val diag = ex.firstError
            val location = diag?.location ?: fail("Location should not be null")
            val message = ex.message

            val expectedLocation = SourceCode.Location(SourceCode.Position(3, 11), SourceCode.Position(3, 14))
            val expectedMessage = "Line_0.${repl.fileExtension} (3:11 - 14) Unresolved reference: ppp"

            assertEquals(expectedLocation, location)
            assertEquals(expectedMessage, message)

            return
        }

        fail("Test should fail with ReplCompilerException")
    }

    @Test
    fun testReplWithReceiver() {
        val value = 5
        val cp = classpath + File(ConstReceiver::class.java.protectionDomain.codeSource.location.toURI().path)
        val repl = ReplForJupyterImpl(resolutionInfoProvider, cp, null, scriptReceivers = listOf(ConstReceiver(value)))
        val res = repl.eval("value")
        assertEquals(value, res.resultValue)
    }

    @Test
    fun testDependsOnAnnotation() {
        repl.eval("@file:DependsOn(\"de.erichseifert.gral:gral-core:0.11\")")
    }

    @Test
    fun testImportResolutionAfterFailure() {
        val errorsRes = repl.listErrorsBlocking("import net.pearx.kasechange.*")
        assertEquals(1, errorsRes.errors.toList().size)

        val res = repl.eval(
            """
            @file:DependsOn("net.pearx.kasechange:kasechange-jvm:1.3.0")
            import net.pearx.kasechange.*
            1
            """.trimIndent()
        )

        assertEquals(1, res.resultValue)
    }

    @Test
    fun testDependsOnAnnotationCompletion() {
        repl.eval(
            """
            @file:Repository("https://repo1.maven.org/maven2/")
            @file:DependsOn("com.github.doyaaaaaken:kotlin-csv-jvm:0.7.3")
            """.trimIndent()
        )

        when (val res = repl.completeBlocking("import com.github.", 18)) {
            is CompletionResult.Success -> res.sortedMatches().contains("doyaaaaaken")
            else -> fail("Completion should be successful")
        }
    }

    @Test
    fun testExternalStaticFunctions() {
        val res = repl.eval(
            """
            @file:DependsOn("src/test/testData/kernelTestPackage-1.0.jar")
            import pack.*
            func()
            """.trimIndent()
        )

        assertEquals(42, res.resultValue)
    }

    @Test
    fun testScriptIsolation() {
        assertFails {
            repl.eval("org.jetbrains.kotlinx.jupyter.ReplLineMagics.use")
        }
    }

    @Test
    fun testDependsOnAnnotations() {
        val res = repl.eval(
            """
            @file:DependsOn("de.erichseifert.gral:gral-core:0.11")
            @file:Repository("https://dl.bintray.com/kotlin/kotlinx")
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.2")
            """.trimIndent()
        )

        val newClasspath = res.metadata.newClasspath
        assertTrue(newClasspath.size >= 2)

        val htmlLibPath = listOf(
            "org.jetbrains.kotlinx",
            "kotlinx-html-jvm",
            "jars",
            "kotlinx-html-jvm"
        ).joinToString(File.separator)
        assertTrue(newClasspath.any { htmlLibPath in it })
    }

    @Test
    fun testCompletionSimple() {
        repl.eval("val foobar = 42")
        repl.eval("var foobaz = 43")

        runBlocking {
            repl.complete("val t = foo", 11) {
                result ->
                if (result is CompletionResult.Success) {
                    assertEquals(arrayListOf("foobar", "foobaz"), result.sortedMatches())
                } else {
                    fail("Result should be success")
                }
            }
        }
    }

    @Test
    fun testNoCompletionAfterNumbers() {
        runBlocking {
            repl.complete("val t = 42", 10) {
                result ->
                if (result is CompletionResult.Success) {
                    assertEquals(emptyList(), result.sortedMatches())
                } else {
                    fail("Result should be success")
                }
            }
        }
    }

    @Test
    fun testCompletionForImplicitReceivers() {
        repl.eval(
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
                if (result is CompletionResult.Success) {
                    assertEquals(arrayListOf("c_meth_z(", "c_prop_x", "c_prop_y", "c_zzz"), result.sortedMatches())
                } else {
                    fail("Result should be success")
                }
            }
        }
    }

    @Test
    fun testErrorsList() {
        repl.eval(
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
                assertEquals(
                    withPath(
                        path,
                        listOf(
                            generateDiagnostic(1, 16, 1, 20, "Type mismatch: inferred type is String but Int was expected", "ERROR"),
                            generateDiagnostic(1, 22, 1, 26, "The floating-point literal does not conform to the expected type String", "ERROR"),
                            generateDiagnostic(2, 14, 2, 19, "Type mismatch: inferred type is String but Int was expected", "ERROR"),
                            generateDiagnostic(3, 9, 3, 13, "Unresolved reference: foob", "ERROR")
                        )
                    ),
                    actualErrors
                )
            }
        }
    }

    @Test
    fun testFreeCompilerArg() {
        runBlocking {
            val res = repl.eval(
                """
                @file:CompilerArgs("-Xopt-in=kotlin.RequiresOptIn")
                """.trimIndent()
            )
            assertEquals(Unit, res.resultValue)

            repl.listErrors(
                """
                import kotlin.time.*
                @OptIn(ExperimentalTime::class)
                val mark = TimeSource.Monotonic.markNow()
                """.trimIndent()
            ) { result ->
                assertEquals(emptyList(), result.errors.toList())
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
                assertEquals(
                    withPath(
                        path,
                        listOf(
                            generateDiagnostic(3, 9, 3, 15, "Unresolved reference: foobar", "ERROR")
                        )
                    ),
                    actualErrors
                )
            }
        }
    }

    @Test
    fun testCompletionWithMagic() {
        repl.eval("val foobar = 42")

        runBlocking {
            val code =
                """
                    
                %trackClasspath
            
                foo
                """.trimIndent()
            repl.complete(code, code.indexOf("foo") + 3) { result ->
                if (result is CompletionResult.Success) {
                    assertEquals(arrayListOf("foobar"), result.sortedMatches())
                } else {
                    fail("Result should be success")
                }
            }
        }
    }

    @Test
    fun testCommands() {
        val code1 = ":help"
        val code2 = ":hex "

        runBlocking {
            repl.listErrors(code1) { result ->
                assertEquals(code1, result.code)
                assertEquals(0, result.errors.toList().size)
            }
            repl.listErrors(code2) { result ->
                assertEquals(code2, result.code)
                val expectedList = listOf(generateDiagnosticFromAbsolute(code2, 0, 4, "Unknown command", "ERROR"))
                val actualList = result.errors.toList()
                assertEquals(expectedList, actualList)
            }
            repl.complete(code2, 3) { result ->
                if (result is CompletionResult.Success) {
                    assertEquals(listOf("help"), result.sortedMatches())
                } else {
                    fail("Result should be success")
                }
            }
        }
    }

    @Test
    fun testEmptyErrorsListJson() {
        val res = ListErrorsResult("someCode")
        assertEquals("""{"code":"someCode","errors":[]}""", Json.encodeToString(res.message))
    }

    @Test
    fun testOut() {
        repl.eval("1+1", null, 1)
        val res = repl.eval("Out[1]")
        assertEquals(2, res.resultValue)
        assertFails { repl.eval("Out[3]") }
    }

    @Test
    fun testOutputMagic() {
        repl.eval("%output --max-cell-size=100500 --no-stdout")
        assertEquals(
            OutputConfig(
                cellOutputMaxSize = 100500,
                captureOutput = false
            ),
            repl.outputConfig
        )

        repl.eval("%output --max-buffer=42 --max-buffer-newline=33 --max-time=2000")
        assertEquals(
            OutputConfig(
                cellOutputMaxSize = 100500,
                captureOutput = false,
                captureBufferMaxSize = 42,
                captureNewlineBufferSize = 33,
                captureBufferTimeLimitMs = 2000
            ),
            repl.outputConfig
        )

        repl.eval("%output --reset-to-defaults")
        assertEquals(OutputConfig(), repl.outputConfig)
    }

    @Test
    fun testJavaRuntimeUtils() {
        val result = repl.eval("JavaRuntimeUtils.version")
        val resultVersion = result.resultValue
        val expectedVersion = JavaRuntime.version
        assertEquals(expectedVersion, resultVersion)
    }

    @Test
    fun testKotlinMath() {
        val result = repl.eval("2.0.pow(2.0)").resultValue
        assertEquals(4.0, result)
    }
}
