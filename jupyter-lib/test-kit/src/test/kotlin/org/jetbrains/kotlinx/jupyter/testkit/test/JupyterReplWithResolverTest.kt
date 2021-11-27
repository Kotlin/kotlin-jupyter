package org.jetbrains.kotlinx.jupyter.testkit.test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException
import org.jetbrains.kotlinx.jupyter.testkit.JupyterReplTestCase
import org.jetbrains.kotlinx.jupyter.testkit.ReplProvider
import org.junit.jupiter.api.Test

class JupyterReplWithResolverTest : JupyterReplTestCase(ReplProvider.withDefaultClasspathResolution { it != "lets-plot" }) {

    @Test
    fun dataframe() {
        val dfHtml = execHtml(
            """
            %use dataframe
            
            val name by column<String>()
            val height by column<Int>()
            
            dataFrameOf(name, height)(
                "Bill", 135,
                "Mark", 160
            )            
            """.trimIndent()
        )

        dfHtml shouldContain "Bill"
    }

    @Test
    fun `lets-plot is not resolved as it is an exception`() {
        shouldThrow<ReplPreprocessingException> {
            exec("%lets-plot")
        }
    }
}
