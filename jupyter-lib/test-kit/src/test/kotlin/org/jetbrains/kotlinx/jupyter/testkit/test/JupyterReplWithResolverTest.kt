package org.jetbrains.kotlinx.jupyter.testkit.test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException
import org.jetbrains.kotlinx.jupyter.testkit.JupyterReplTestCase
import org.jetbrains.kotlinx.jupyter.testkit.ReplProvider
import org.junit.jupiter.api.Test

class JupyterReplWithResolverTest : JupyterReplTestCase(
    ReplProvider.withDefaultClasspathResolution(
        shouldResolve = { it != "lets-plot" },
        shouldResolveToEmpty = { it == "multik" },
    ),
) {

    @Test
    fun dataframe() {
        val dfHtml = execHtml(
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
    fun `lets-plot is not resolved as it is an exception`() {
        val exception = shouldThrow<ReplPreprocessingException> {
            exec("%use lets-plot")
        }
        exception.message shouldContain "Unknown library"
    }

    @Test
    fun `multik resolves to empty`() {
        exec("%use multik")

        val exception = shouldThrow<ReplCompilerException> {
            exec("import org.jetbrains.kotlinx.multik.api.*")
        }
        exception.message shouldContain "Unresolved reference: multik"
    }
}
