package org.jetbrains.kotlinx.jupyter.api

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.lang.reflect.Array
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

/**
 * Renders any array (primitive or non-primitive) into list
 */
val arrayRenderer = object : RendererHandler {
    override fun accepts(value: Any?): Boolean {
        return value != null && value::class.java.isArray
    }

    private fun toListRuntime(a: Any): List<Any?> {
        val len = Array.getLength(a)
        return ArrayList<Any?>(len).apply {
            for (i in 0 until len) {
                add(Array.get(a, i))
            }
        }
    }

    override val execution = ResultHandlerExecution { _, result -> FieldValue(toListRuntime(result.value!!), result.name) }
    override fun replaceVariables(mapping: Map<String, String>) = this
}
