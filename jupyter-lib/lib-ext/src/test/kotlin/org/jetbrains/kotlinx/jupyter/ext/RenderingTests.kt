package org.jetbrains.kotlinx.jupyter.ext

import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import java.io.Writer
import kotlin.test.assertEquals

class RenderingTests {
    @Test
    fun testRendering() {
        val pngFile = objectsDir.resolve("logo.png")
        val pngFileRelative = "../${objectsDir.name}/logo.png"
        val image1 = Image(pngFile, true)
        val image2 = Image(pngFileRelative, false)
        val image3 = image2.withHeight(200)

        val svgFile = objectsDir.resolve("svg_ex.svg")
        val svg1 = Image(svgFile, true)

        assertHtmlEquals("test1.html") {
            appendLine(image1.toHTML())
            appendLine(image2.toHTML())
            appendLine(image3.toHTML())
            appendLine(svg1.toHTML())
        }
    }

    @Test
    fun testLatex() {
        val latex = """
            \[
             \lim_{x\to 0}{\frac{e^x-1}{2x}}
             \overset{\left[\frac{0}{0}\right]}{\underset{\mathrm{H}}{=}}
             \lim_{x\to 0}{\frac{e^x}{2}}={\frac{1}{2}}
            \]
        """.trimIndent()
        val img = renderLatex(latex)

        assertHtmlEquals("test2.html") {
            appendLine(img.toHTML())
        }
    }

    private fun assertHtmlEquals(fileName: String, contentWriteAction: Writer.() -> Unit) {
        val file = renderedDir.resolve(fileName)
        val writer = if (doRegenerate) FileOutputStream(file).writer() else StringWriter()
        with(writer) {
            appendLine("<html><head></head><body>")
            contentWriteAction()
            appendLine("</body></html>")
            close()
        }

        if (!doRegenerate) {
            val actualVal = writer.toString()
            assertEquals(file.readText(), actualVal)
        }
    }

    companion object {
        private const val doRegenerate = false

        private val dataDir = File("src/test/testData")
        private val objectsDir = dataDir.resolve("objectsToRender")
        private val renderedDir = dataDir.resolve("renderedObjects")
    }
}
