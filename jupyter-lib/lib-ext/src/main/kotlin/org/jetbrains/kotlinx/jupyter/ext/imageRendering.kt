package org.jetbrains.kotlinx.jupyter.ext

import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.jetbrains.kotlinx.jupyter.api.HTML
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.Renderable
import java.io.File
import java.net.URI
import java.util.Base64

data class HTMLAttr(val name: String, val value: String)

class Image(private val attributes: List<HTMLAttr>) : Renderable {
    override fun render(notebook: Notebook): MimeTypedResult {
        return HTML(
            attributes.joinToString("", """<img""", """/>""") {
                """ ${it.name}="${it.value}""""
            }
        )
    }

    constructor(url: String, embed: Boolean = false) : this(
        listOf(
            if (embed) embedSrc(downloadData(url), detectFormat(URI(url)))
            else referSrc(url)
        )
    )

    constructor(file: File, embed: Boolean = false) : this(
        listOf(
            if (embed) embedSrc(loadData(file), detectFormat(file.toURI()))
            else referSrc(file.toURI().toASCIIString())
        )
    )

    constructor(data: ByteArray, format: String) : this(
        listOf(
            embedSrc(data, format)
        )
    )

    fun withAttr(attr: HTMLAttr) = Image(attributes + attr)
    fun withAttr(name: String, value: String) = withAttr(HTMLAttr(name, value))

    fun withWidth(value: String) = withAttr("width", value)
    fun withHeight(value: String) = withAttr("height", value)

    companion object {
        fun referSrc(url: String): HTMLAttr {
            return HTMLAttr("src", url)
        }

        fun embedSrc(data: ByteArray, format: String): HTMLAttr {
            val encoder = Base64.getEncoder()
            return HTMLAttr(
                "src",
                buildString {
                    append("""data:image/$format;base64,""")
                    append(encoder.encodeToString(data))
                }
            )
        }

        fun downloadData(url: String): ByteArray {
            val client = ApacheClient()
            val request = Request(Method.GET, url)
            val response = client(request)
            return response.body.payload.array()
        }

        fun loadData(file: File): ByteArray {
            return file.readBytes()
        }

        fun detectFormat(uri: URI): String {
            return uri.toString().substringAfterLast('.', "")
        }
    }
}
