package org.jetbrains.kotlinx.jupyter.ext

import io.kotest.matchers.collections.shouldBeSameSizeAs
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.api.graphs.GraphNode
import org.jetbrains.kotlinx.jupyter.ext.graph.structure.Graph
import org.jetbrains.kotlinx.jupyter.ext.graph.visualization.toHTML
import org.jetbrains.kotlinx.jupyter.ext.graph.wrappers.fromClass
import org.jetbrains.kotlinx.jupyter.ext.graph.wrappers.fromClassLoader
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import java.io.Writer
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderingTests {
    @Test
    fun testRendering() {
        val pngFile = objectsDir.resolve("logo.png")
        val pngFileRelative = "../${objectsDir.name}/logo.png"
        val image1 = Image(pngFile, true)
        val image2 = Image(pngFileRelative, false)
        val image3 = image2.withHeight(200)

        val bufferedImage = ImageIO.read(pngFile)
        val image4 = Image(bufferedImage)

        val svgFile = objectsDir.resolve("svg_ex.svg")
        val svg1 = Image(svgFile, true)

        assertHtmlEquals(
            "test1.html",
            asserter = AlmostEqualsMultilineComparator(1)::compare,
        ) {
            appendLine(image1.toHTML())
            appendLine(image2.toHTML())
            appendLine(image3.toHTML())
            appendLine(svg1.toHTML())
            appendLine(image4.toHTML())
        }
    }

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

        LATEX(latex)

        // Rendered image is platform-dependent, skip this assertion
        // assertHtmlEquals("test2.html") {
        //     appendLine(img.toHTML())
        // }
    }

    @Test
    @Disabled
    fun testGraphVisualization() {
        val html1 = Graph.of(GraphNode.fromClass<StringWriter>()).toHTML()
        assertTrue(html1.length > 1000)
        val html2 = Graph.of(GraphNode.fromClassLoader<RenderingTests>()).toHTML()
        assertTrue(html2.length > 1000)
    }

    private fun assertHtmlEquals(
        @Suppress("SameParameterValue") fileName: String,
        asserter: (String, String) -> Unit = { expected, actual -> actual shouldBe expected },
        contentWriteAction: Writer.() -> Unit,
    ) {
        val file = renderedDir.resolve(fileName)
        val writer = if (DO_REGENERATE) FileOutputStream(file).writer() else StringWriter()
        with(writer) {
            appendLine("<html><head></head><body>")
            contentWriteAction()
            appendLine("</body></html>")
            close()
        }

        if (!DO_REGENERATE) {
            val actualVal = writer.toString()
            asserter(file.readText(), actualVal)
        }
    }

    private class AlmostEqualsMultilineComparator(
        val mayDiverge: Int,
    ) {
        fun compare(
            expected: String,
            actual: String,
        ) {
            val expectedLines = expected.lines()
            val actualLines = actual.lines()

            actualLines shouldBeSameSizeAs expectedLines

            val diverged = expectedLines.zip(actualLines).count { it.first != it.second }
            if (diverged > mayDiverge) {
                assertEquals(expected, actual, "Texts diverge more than in $mayDiverge lines (in $diverged, actually)")
            }
        }
    }

    companion object {
        private const val DO_REGENERATE = false

        private val dataDir = File("src/test/testData")
        private val objectsDir = dataDir.resolve("objectsToRender")
        private val renderedDir = dataDir.resolve("renderedObjects")
    }
}
