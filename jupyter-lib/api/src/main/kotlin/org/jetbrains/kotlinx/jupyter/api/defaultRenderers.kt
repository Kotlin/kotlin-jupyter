package org.jetbrains.kotlinx.jupyter.api

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

val bufferedImageRenderer = createRenderer<BufferedImage> {
    val format = "png"
    val stream = ByteArrayOutputStream()
    ImageIO.write(it, format, stream)
    val data = stream.toByteArray()
    val encoder = Base64.getEncoder()
    val src = buildString {
        append("""data:image/$format;base64,""")
        append(encoder.encodeToString(data))
    }
    HTML("""<img src="$src"/>""")
}
