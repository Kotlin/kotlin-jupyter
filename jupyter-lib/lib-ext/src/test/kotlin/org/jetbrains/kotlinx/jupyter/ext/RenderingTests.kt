package org.jetbrains.kotlinx.jupyter.ext

import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
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

        val res = renderedDir.resolve("test1.html")

        // Uncomment to regenerate
        // val writer = FileOutputStream(res).writer()
        val writer = StringWriter()

        writer.appendLine("<html><head></head><body>")
        writer.appendLine(image1.toHTML())
        writer.appendLine(image2.toHTML())
        writer.appendLine(image3.toHTML())
        writer.appendLine(svg1.toHTML())
        writer.appendLine("</body></html>")
        writer.close()

        val actualVal = writer.toString()

        assertEquals(res.readText(), actualVal)
    }

    companion object {
        private val dataDir = File("src/test/testData")
        private val objectsDir = dataDir.resolve("objectsToRender")
        private val renderedDir = dataDir.resolve("renderedObjects")
    }
}
