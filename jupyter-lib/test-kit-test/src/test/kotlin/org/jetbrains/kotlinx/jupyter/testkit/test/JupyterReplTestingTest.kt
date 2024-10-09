package org.jetbrains.kotlinx.jupyter.testkit.test

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException
import org.jetbrains.kotlinx.jupyter.testkit.JupyterReplTestCase
import org.junit.jupiter.api.Test

class JupyterReplTestingTest : JupyterReplTestCase() {
    @Test
    fun `raw execution`() {
        val result = execRaw("2 + 2")
        result shouldBe 4
    }

    @Test
    fun `normal (rendered) execution`() {
        val result = execRendered("42")
        result shouldBe 42
    }

    @Test
    fun `extended execution`() {
        execRendered(
            """
            USE {
                render<Int> { (it * 2).toString() }
            }
            """.trimIndent(),
        )

        val ex = execSuccess("5")

        ex.internalResult.result.value shouldBe 5
        ex.renderedValue shouldBe "10"
    }

    @Test
    fun `HTML execution`() {
        execRendered(
            """
            USE {
                render<Int> { HTML((it * 2).toString()) }
            }
            """.trimIndent(),
        )

        execRendered("5").shouldBeInstanceOf<MimeTypedResult>()
        execHtml("5") shouldBe "10"
    }

    @Test
    fun `dataframe is not resolved`() {
        execError("%dataframe").shouldBeTypeOf<ReplPreprocessingException>()
    }
}
