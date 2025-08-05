package org.jetbrains.kotlinx.jupyter.ext

import org.http4k.client.ApacheClient
import org.http4k.client.PreCannedApacheHttpClients
import org.http4k.core.Method
import org.http4k.core.Request
import org.jetbrains.kotlinx.jupyter.api.HTML
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.Renderable
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.Base64
import javax.imageio.ImageIO

class Image(
    private val attributes: List<HTMLAttr>,
) : Renderable {
    override fun render(notebook: Notebook): MimeTypedResult = HTML(toHTML())

    fun toHTML(): String =
        attributes.joinToString("", """<img""", """/>""") {
            """ ${it.name}="${it.value}""""
        }

    constructor(url: String, embed: Boolean = false) : this(
        listOf(
            if (embed) {
                embedSrc(downloadData(url), detectMime(URI(url)))
            } else {
                referSrc(url)
            },
        ),
    )

    constructor(file: File, embed: Boolean = false) : this(
        listOf(
            if (embed) {
                embedSrc(loadData(file), detectMime(file.toURI()))
            } else {
                referSrc(file.absolutePath.toString())
            },
        ),
    )

    constructor(data: ByteArray, format: String) : this(
        listOf(
            embedSrc(data, convertFormat(format)),
        ),
    )

    constructor(data: BufferedImage, outputFormat: String = "png") :
        this(data.toByteArray(outputFormat), outputFormat)

    fun withAttr(attr: HTMLAttr) = Image(attributes + attr)

    fun withAttr(
        name: String,
        value: String,
    ) = withAttr(HTMLAttr(name, value))

    fun withWidth(value: String) = withAttr("width", value)

    fun withWidth(value: Int) = withAttr("width", value.toString())

    fun withHeight(value: String) = withAttr("height", value)

    fun withHeight(value: Int) = withAttr("height", value.toString())

    companion object {
        private val formatToMime =
            mapOf(
                "svg" to "svg+xml",
            )

        private fun BufferedImage.toByteArray(format: String): ByteArray {
            val stream = ByteArrayOutputStream()
            ImageIO.write(this, format, stream)
            return stream.toByteArray()
        }

        fun referSrc(url: String): HTMLAttr = HTMLAttr("src", url)

        fun embedSrc(
            data: ByteArray,
            format: String,
        ): HTMLAttr {
            val encoder = Base64.getEncoder()
            return HTMLAttr(
                "src",
                buildString {
                    append("""data:image/$format;base64,""")
                    append(encoder.encodeToString(data))
                },
            )
        }

        fun downloadData(url: String): ByteArray {
            PreCannedApacheHttpClients.defaultApacheHttpClient().use { closeableHttpClient ->
                val client = ApacheClient(client = closeableHttpClient)
                val request = Request(Method.GET, url)
                val response = client(request)
                return response.body.payload.array()
            }
        }

        fun loadData(file: File): ByteArray = file.readBytes()

        fun detectMime(uri: URI): String {
            val format = uri.toString().substringAfterLast('.', "")
            return convertFormat(format)
        }

        fun convertFormat(format: String) = format.lowercase().let { formatToMime[it] ?: it }
    }
}
