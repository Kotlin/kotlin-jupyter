package org.jetbrains.kotlinx.jupyter.test

import jupyter.kotlin.receivers.TempAnnotation
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.scripting.ide_services.compiler.KJvmReplCompilerWithIdeServices
import org.jetbrains.kotlinx.jupyter.EvalResult
import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.compiler.util.SourceCodeImpl
import org.jetbrains.kotlinx.jupyter.repl.impl.getSimpleCompiler
import org.jetbrains.kotlinx.jupyter.test.repl.AbstractReplTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiTest : AbstractReplTest() {
    private val repl = ReplForJupyterImpl(resolutionInfoProvider, classpath)

    private fun jEval(jupyterId: Int, code: String): EvalResult {
        return repl.eval(code, jupyterId = jupyterId)
    }

    @Test
    fun testRepl() {
        jEval(1, "val x = 3")
        jEval(2, "x*2")
        jEval(
            3,
            """
            println(x*3)
            """.trimIndent()
        )
        val res1 = jEval(4, "notebook.cells[2]?.result")
        assertEquals(6, res1.resultValue)
    }

    @Test
    fun compilerVersion() {
        val jCompiler = getSimpleCompiler(
            ScriptCompilationConfiguration(),
            ScriptEvaluationConfiguration()
        )
        val version = jCompiler.version
        assertTrue(version.major >= 0)
    }

    @Test
    @Disabled // TODO: waiting for fix https://youtrack.jetbrains.com/issue/KT-44580
    fun fileAnnotationsTest() {

        runBlocking {

            val config = ScriptCompilationConfiguration()

            val compiler = KJvmReplCompilerWithIdeServices(defaultJvmScriptingHostConfiguration)

            compiler.compile(SourceCodeImpl(1, ""), config)

            var handlerInvoked = false

            val config2 = config.with {
                refineConfiguration {
                    onAnnotations<TempAnnotation> {
                        handlerInvoked = true
                        it.compilationConfiguration.asSuccess()
                    }
                }
            }

            compiler.compile(SourceCodeImpl(2, "@file:TempAnnotation"), config2)

            assertTrue(handlerInvoked)
        }
    }
}
