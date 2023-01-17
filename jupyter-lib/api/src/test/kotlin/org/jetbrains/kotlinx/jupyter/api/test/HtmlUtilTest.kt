package org.jetbrains.kotlinx.jupyter.api.test

import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.util.escapeForIframe
import org.junit.jupiter.api.Test
import java.io.File

class HtmlUtilTest {
    private val testFolder = File("src/test/testData/html")

    @Test
    fun `escape iframe source`() {
        val text = testFolder.resolve("iframeEscape.html").readText()
        val actualResult = text.escapeForIframe()
        val expectedResult = testFolder.resolve("iframeEscapeResult.html").readText()
        actualResult shouldBe expectedResult
    }
}
