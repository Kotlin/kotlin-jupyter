package org.jetbrains.kotlinx.jupyter.api

import java.awt.GraphicsConfiguration
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame

/**
 * Takes a screenshot of a [JDialog].
 */
fun JDialog.takeScreenshot(): BufferedImage? {
    return rootPane.takeScreenshot()
}

/**
 * Takes a screenshot of a [JFrame].
 */
fun JFrame.takeScreenshot(): BufferedImage? {
    return rootPane.takeScreenshot()
}

/**
 * Takes a screenshot of the Swing component. This is only possible if the
 * component has been given a size, see [JComponent.getSize]. Either manually
 * or through a [java.awt.LayoutManager].
 *
 * If the size of the component cannot be determined, `null` is returned.
 */
fun JComponent.takeScreenshot(): BufferedImage? {
    try {
        val config: GraphicsConfiguration? = graphicsConfiguration
        val scaleFactor: Double = config?.defaultTransform?.scaleX ?: 1.0
        if (!isVisible || width == 0 || height == 0) return null
        val image = BufferedImage((width * scaleFactor).toInt(), (height * scaleFactor).toInt(), BufferedImage.TYPE_INT_ARGB)
        with(image.createGraphics()) {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            scale(scaleFactor, scaleFactor)
            paint(this)
            dispose()
        }
        return image
    } catch (ignore: Exception) {
        // In case something goes wrong, do not crash the application as fallback
        // images are just nice to have, not required.
        return null
    }
}
