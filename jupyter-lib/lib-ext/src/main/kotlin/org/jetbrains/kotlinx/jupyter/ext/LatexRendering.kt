/**
 * This file includes modified code of JLaTeXMath library.
 * Library source: https://github.com/opencollab/jlatexmath
 * JLaTeXMath is licensed under GPL 2.0 license,
 * the full license text and copyright notes are given in
 * additional-licenses/jlatexmath/COPYING and
 * additional-licenses/jlatexmath/LICENSE files.
 */

package org.jetbrains.kotlinx.jupyter.ext

import org.apache.batik.dom.GenericDOMImplementation
import org.apache.batik.svggen.SVGGeneratorContext
import org.apache.batik.svggen.SVGGraphics2D
import org.scilab.forge.jlatexmath.DefaultTeXFont
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.cyrillic.CyrillicRegistration
import org.scilab.forge.jlatexmath.greek.GreekRegistration
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import javax.swing.JLabel

@Suppress("FunctionName")
fun LATEX(
    latex: String,
    fontAsShapes: Boolean = true,
): Image {
    val data = latexToSvgData(latex, fontAsShapes)
    return Image(data, "svg")
}

private fun latexToSvgData(
    latex: String,
    fontAsShapes: Boolean,
): ByteArray {
    val domImpl = GenericDOMImplementation.getDOMImplementation()
    val svgNS = "http://www.w3.org/2000/svg"
    val document = domImpl.createDocument(svgNS, "svg", null)
    val ctx = SVGGeneratorContext.createDefault(document)
    val g2 = SVGGraphics2D(ctx, fontAsShapes)
    DefaultTeXFont.registerAlphabet(CyrillicRegistration())
    DefaultTeXFont.registerAlphabet(GreekRegistration())
    val formula = TeXFormula(latex)
    val icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 20f)
    icon.insets = Insets(5, 5, 5, 5)
    g2.svgCanvasSize = Dimension(icon.iconWidth, icon.iconHeight)
    g2.color = Color.white
    g2.fillRect(0, 0, icon.iconWidth, icon.iconHeight)
    val jl = JLabel()
    jl.foreground = Color(0, 0, 0)
    icon.paintIcon(jl, g2, 0, 0)
    val useCSS = true
    val byteStream = ByteArrayOutputStream()
    val out = OutputStreamWriter(byteStream, "UTF-8")

    g2.stream(out, useCSS)

    return byteStream.toByteArray()
}
