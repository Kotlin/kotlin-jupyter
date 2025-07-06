package org.jetbrains.kotlinx.jupyter.testkit.test

import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode.K1
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode.K2
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException
import org.jetbrains.kotlinx.jupyter.repl.result.EvalResultEx
import org.jetbrains.kotlinx.jupyter.testkit.JupyterReplTestCase
import org.jetbrains.kotlinx.jupyter.testkit.ReplProvider
import org.junit.jupiter.api.Test

class JupyterReplWithResolverTest :
    JupyterReplTestCase(
        ReplProvider.withDefaultClasspathResolution(
            shouldResolve = { it != "lets-plot" },
            shouldResolveToEmpty = { it == "multik" },
        ),
    ) {
    @Test
    fun dataframe() {
        val dfHtml =
            execHtml(
                """
                %use dataframe()
                
                val name by column<String>()
                val height by column<Int>()
                
                dataFrameOf(name, height)(
                    "Bill", 135,
                    "Mark", 160
                )            
                """.trimIndent(),
            )

        dfHtml shouldContain "Bill"
    }

    @Test
    fun `failed code with use should still provide metadata`() {
        execEx(
            """
            SessionOptions.serializeScriptData = true
            """.trimIndent(),
        )

        val result =
            execEx(
                """
                %use dataframe
                throw Exception()
                """.trimIndent(),
            )

        result.shouldBeTypeOf<EvalResultEx.Error>()
        with(result.metadata) {
            newImports.shouldHaveAtLeastSize(10)
            compiledData.scripts.shouldHaveAtLeastSize(2)
        }
    }

    @Test
    fun `lets-plot is not resolved as it is an exception`() {
        val exception = execError("%use lets-plot")
        exception.shouldBeTypeOf<ReplPreprocessingException>()
        exception.message shouldContain "Unknown library"
    }

    @Test
    fun `multik resolves to empty`() {
        execRendered("%use multik")

        val exception = execError("import org.jetbrains.kotlinx.multik.api.*")
        exception.shouldBeTypeOf<ReplCompilerException>()
        exception.message shouldContain
            when (compilerMode) {
                K1 -> "Unresolved reference: multik"
                K2 -> "Unresolved reference 'multik'"
            }
    }

    @Test
    fun `datetime is loaded and present`() {
        execRendered("Clock.System.now()")
    }
}
