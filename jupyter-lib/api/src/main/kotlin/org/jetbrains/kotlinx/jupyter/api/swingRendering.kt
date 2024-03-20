package org.jetbrains.kotlinx.jupyter.api

import java.awt.GraphicsConfiguration
import java.awt.Image
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Robot
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Takes a screenshot of a [JFrame]. Since we cannot reliably guarantee to take an image of the full frame
 * including platform specific UI elements (like border + controls) we just take a screenshot of the content
 * instead.
 *
 * TODO Verify this
 */
fun JFrame.takeScreenshot(): BufferedImage {
    // Ensure the frame is fully rendered before taking the screenshot
    try {
        SwingUtilities.invokeAndWait { repaint() }
    } catch (ignore: Exception) { }

    // For retina displays, pick the high-resolution image and downscale it again for a better
    // screenshot. This is only possible on Java 9+, so for other versions, just do a basic
    // screen capture.
    val java9OrLater = !System.getProperty("java.version").startsWith("1.")
    val captureRect = Rectangle(bounds.x, bounds.y, bounds.width, bounds.height)
    return if (java9OrLater) {
        @Suppress("Since15")
        val results = Robot().createMultiResolutionScreenCapture(captureRect)
        // Do not down-scale high-resolution screenshots
        @Suppress("Since15")
        return results.resolutionVariants.last().asBufferedImage(1.0)
    } else {
        Robot().createScreenCapture(captureRect)
    }
}

/**
 * Takes a screenshot of the Swing component. This is only possible if the
 * component has been given a size, see [JComponent.getSize]. Either manually
 * or through a [java.awt.LayoutManager].
 *
 * If the size of the component cannot be determined, `null` is returned.
 */
fun JComponent.takeScreenshot(): BufferedImage? {
    val config: GraphicsConfiguration? = graphicsConfiguration
    val scaleFactor: Double = config?.defaultTransform?.scaleX ?: 1.0
    if (width == 0 || height == 0) return null
    val image = BufferedImage((width * scaleFactor).toInt(), (height * scaleFactor).toInt(), BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    paint(graphics)
    graphics.dispose()
    return image
}

/**
 * Converts an [Image] to a [BufferedImage], potentially scaling it
 * in the process. [BufferedImage]'s can be serialized and sent to
 * Notebook clients using [encodeBufferedImage].
 */
fun Image.asBufferedImage(scale: Double = 1.0): BufferedImage {
    // Return underlying BufferedImage immediately if possible
    if (scale == 1.0 && this is BufferedImage) {
        return this
    }
    // Otherwise create a new image we can write to
    val bufferedImage = BufferedImage(
        (getWidth(null) * scale).toInt(),
        (getHeight(null) * scale).toInt(),
        BufferedImage.TYPE_INT_ARGB
    )
    // Write the current image into the new image and scale it in the process.
    // RenderingHints are applied to make any scaling appear "nicer" without jagged edges.
    with(bufferedImage.createGraphics()) {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        scale(scale, scale)
        drawImage(this@asBufferedImage, 0, 0, null)
        dispose()
    }
    return bufferedImage
}
