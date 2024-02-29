package org.jetbrains.kotlinx.jupyter.api

import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities

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

fun JPanel.takeScreenshot(): BufferedImage {
    // Ensure the frame is fully rendered before taking the screenshot
    try {
        SwingUtilities.invokeAndWait { repaint() }
    } catch (ignore: Exception) { }
    val config: GraphicsConfiguration? = graphicsConfiguration
    val scaleFactor: Double = config?.defaultTransform?.scaleX ?: 1.0
    val image = BufferedImage((width * scaleFactor).toInt(), (height * scaleFactor).toInt(), BufferedImage.TYPE_INT_RGB)
    with(image.createGraphics()) {
        scale(scaleFactor, scaleFactor)
        paint(this)
        dispose()
    }
    return image
}

fun Image.asBufferedImage(scale: Double = 1.0): BufferedImage {
    val bufferedImage = BufferedImage(
        (getWidth(null) * scale).toInt(),
        (getHeight(null) * scale).toInt(),
        BufferedImage.TYPE_INT_ARGB
    )
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
