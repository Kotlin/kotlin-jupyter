package org.jetbrains.kotlinx.jupyter.api.test

import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.util.escapeForIframe
import org.junit.jupiter.api.Test
import java.io.File

class HtmlUtilTest {
    private val testFolder = File("src/test/testData/html")
    private val doRegenerate = false

    private fun testEquality(
        sourceFile: String,
        expectedFile: String,
        transformer: (String) -> String,
    ) {
        val sourceText = testFolder.resolve(sourceFile).readText()
        val actualResult = transformer(sourceText)
        if (doRegenerate) {
            testFolder.resolve(expectedFile).writeText(actualResult)
        } else {
            val expectedResult = testFolder.resolve(expectedFile).readText()
            actualResult shouldBe expectedResult
        }
    }

    @Test
    fun `escape iframe source`() =
        testEquality("iframeEscape.html", "iframeEscapeResult.html") { text ->
            text.escapeForIframe()
        }
}
