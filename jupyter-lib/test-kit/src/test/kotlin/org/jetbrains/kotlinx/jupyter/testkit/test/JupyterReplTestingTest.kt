package org.jetbrains.kotlinx.jupyter.testkit.test

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
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
        val result = exec("42")
        result shouldBe 42
    }

    @Test
    fun `extended execution`() {
        exec(
            """
            USE {
                render<Int> { (it * 2).toString() }
            }
            """.trimIndent()
        )

        val ex = execEx("5")

        ex.rawValue shouldBe 5
        ex.renderedValue shouldBe "10"
    }

    @Test
    fun `HTML execution`() {
        exec(
            """
            USE {
                render<Int> { HTML((it * 2).toString()) }
            }
            """.trimIndent()
        )

        exec("5").shouldBeInstanceOf<MimeTypedResult>()
        execHtml("5") shouldBe "10"
    }
}
