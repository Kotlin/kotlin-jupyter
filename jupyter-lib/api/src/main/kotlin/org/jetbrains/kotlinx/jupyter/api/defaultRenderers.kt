package org.jetbrains.kotlinx.jupyter.api

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
    val encodedData = encoder.encodeToString(data)
    MimeTypedResultEx(
        buildJsonObject {
            put(MimeTypes.PNG, JsonPrimitive(encodedData))
            put(MimeTypes.PLAIN_TEXT, JsonPrimitive("${it::class}: ${it.width}x${it.height} px"))
        },
    )
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

    override val execution = ResultHandlerExecution { _, result -> FieldValue(toListRuntime(result.value!!), null) }
    override fun replaceVariables(mapping: Map<String, String>) = this

    override fun toString(): String {
        return "Default renderer of arrays: renders them to lists"
    }
}
