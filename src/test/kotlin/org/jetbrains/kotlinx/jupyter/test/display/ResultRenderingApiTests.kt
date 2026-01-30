package org.jetbrains.kotlinx.jupyter.test.display

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.kotlinx.jupyter.api.LATEX
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.MimeTypes
import org.junit.jupiter.api.Test

class ResultRenderingApiTests {
    @Test
    fun testLatex() {
        val latex =
            """
            \[
             \lim_{x\to 0}{\frac{e^x-1}{2x}}
             \overset{\left[\frac{0}{0}\right]}{\underset{\mathrm{H}}{=}}
             \lim_{x\to 0}{\frac{e^x}{2}}={\frac{1}{2}}
            \]
            """.trimIndent()

        val renderedResult = LATEX(latex).shouldBeInstanceOf<MimeTypedResult>()
        val mimeEntry = renderedResult.entries.single()
        mimeEntry.key shouldBe MimeTypes.LATEX
        mimeEntry.value shouldBe latex
    }
}
