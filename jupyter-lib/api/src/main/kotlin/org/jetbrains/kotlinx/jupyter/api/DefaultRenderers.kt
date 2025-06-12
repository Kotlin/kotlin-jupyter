package org.jetbrains.kotlinx.jupyter.api

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.lang.reflect.Array
import java.util.Base64
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame

/**
 * Convert a buffered image to a PNG file encoded as a Base64 Json string.
 */
fun encodeBufferedImage(image: BufferedImage): JsonPrimitive {
    val format = "png"
    val stream = ByteArrayOutputStream()
    ImageIO.write(image, format, stream)
    val data = stream.toByteArray()
    val encoder = Base64.getEncoder()
    val encodedData = encoder.encodeToString(data)
    return JsonPrimitive(encodedData)
}

fun RendererFieldHandler.named(name: String): RendererFieldHandler =
    object : RendererFieldHandler by this {
        override fun toString(): String = name
    }

val bufferedImageRenderer: RendererFieldHandler =
    createRenderer<BufferedImage> {
        val encodedData: JsonPrimitive = encodeBufferedImage(it)
        MimeTypedResultEx(
            buildJsonObject {
                put(MimeTypes.PNG, encodedData)
                put(MimeTypes.PLAIN_TEXT, JsonPrimitive("${it::class}: ${it.width}x${it.height} px"))
            },
            metadataModifiers = listOf(),
        )
    }.named("Default BufferedImage renderer")

/**
 * Renders any array (primitive or non-primitive) into a list.
 */
val arrayRenderer =
    object : RendererHandler {
        override fun accepts(value: Any?): Boolean = value != null && value::class.java.isArray

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

        override fun toString(): String = "Default renderer of arrays: renders them to lists"
    }

private fun createSwingInMemoryMimeTypedResult(
    fallbackImage: BufferedImage?,
    swingResult: Any,
): InMemoryMimeTypedResult {
    val fallback =
        if (fallbackImage == null) {
            MimeTypes.PLAIN_TEXT to JsonPrimitive("No data available. Rerun the cell.")
        } else {
            MimeTypes.PNG to encodeBufferedImage(fallbackImage)
        }
    return InMemoryMimeTypedResult(
        InMemoryResult(InMemoryMimeTypes.SWING, swingResult),
        mapOf(fallback),
    )
}

/**
 * Renders a Swing [JFrame] in-memory, but also provides a screenshot of the UI as
 * fallback data.
 */
val swingJFrameInMemoryRenderer =
    createRenderer<JFrame> { frame: JFrame ->
        val fallbackImage: BufferedImage? = frame.takeScreenshot()
        createSwingInMemoryMimeTypedResult(fallbackImage, frame)
    }.named("Default Swing JFrame renderer")

/**
 * Renders a Swing [JDialog] in-memory, but also provides a screenshot of the UI as
 * fallback data.
 */
val swingJDialogInMemoryRenderer =
    createRenderer<JDialog> { dialog: JDialog ->
        val fallbackImage: BufferedImage? = dialog.takeScreenshot()
        createSwingInMemoryMimeTypedResult(fallbackImage, dialog)
    }.named("Default Swing JDialog renderer")

/**
 * Renders a Swing [JComponent] in-memory, but also provides a screenshot of the UI as
 * fallback data.
 */
val swingJComponentInMemoryRenderer: RendererFieldHandler =
    createRenderer<JComponent> { component: JComponent ->
        val fallbackImage: BufferedImage? = component.takeScreenshot()
        createSwingInMemoryMimeTypedResult(fallbackImage, component)
    }.named("Default Swing JComponent renderer")
