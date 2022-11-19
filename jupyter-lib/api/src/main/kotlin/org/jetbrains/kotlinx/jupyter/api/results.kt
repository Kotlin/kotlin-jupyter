package org.jetbrains.kotlinx.jupyter.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import org.jetbrains.kotlinx.jupyter.util.EMPTY
import java.util.concurrent.atomic.AtomicLong

/**
 * Type alias for FQNs - fully qualified names of classes
 */
typealias TypeName = String

/**
 * Type alias for plain code ready for execution
 */
typealias Code = String

/**
 * Object that should be rendered to [DisplayResult] if
 * it is the result of code cell
 */
interface Renderable {
    /**
     * Render to display result
     *
     * @param notebook Current notebook
     * @return Display result
     */
    fun render(notebook: Notebook): DisplayResult
}

/**
 * Display result that may be converted to JSON for `display_data`
 * kernel response
 */
interface DisplayResult : Renderable {
    /**
     * Unique id that may be used for updating display data
     */
    val id: String? get() = null

    /**
     * Converts display data to JSON object for `display_data` response
     *
     * @param additionalMetadata Additional reply metadata
     * @return Display JSON
     */
    fun toJson(additionalMetadata: JsonObject = Json.EMPTY, overrideId: String? = null): JsonObject

    @Deprecated("Use full version instead", ReplaceWith("toJson(additionalMetadata, null)"))
    fun toJson(additionalMetadata: JsonObject = Json.EMPTY): JsonObject = toJson(additionalMetadata, null)

    /**
     * Renders display result, generally should return `this`
     */
    override fun render(notebook: Notebook) = this
}

/**
 * Display result that holds the reference to related cell
 */
interface DisplayResultWithCell : DisplayResult {
    val cell: CodeCell
}

/**
 * Container that holds all notebook display results
 */
interface DisplayContainer {
    fun getAll(): List<DisplayResultWithCell>
    fun getById(id: String?): List<DisplayResultWithCell>
}

typealias MutableJsonObject = MutableMap<String, JsonElement>

/**
 * Convenience method for converting nullable [DisplayResult] to JSON
 *
 * @return JSON for `display_data` response
 */
@Suppress("unused")
fun DisplayResult?.toJson(): JsonObject {
    if (this != null) return this.toJson(Json.EMPTY, null)
    return Json.encodeToJsonElement(mapOf("data" to null, "metadata" to JsonObject(mapOf()))) as JsonObject
}

@Suppress("unused")
fun DisplayResult.withId(id: String) = if (id == this.id) this else object : DisplayResult {
    override fun toJson(additionalMetadata: JsonObject, overrideId: String?) = this@withId.toJson(additionalMetadata, overrideId ?: id)
    override val id = id
}

/**
 * Sets display ID to JSON.
 * If ID was not set, sets it to [id] and returns it back
 * If ID was set and [force] is false, just returns old ID
 * If ID was set, [force] is true and [id] is `null`, just returns old ID
 * If ID was set, [force] is true and [id] is not `null`, sets ID to [id] and returns it back
 */
fun MutableJsonObject.setDisplayId(id: String? = null, force: Boolean = false): String? {
    val transient = get("transient")?.let { Json.decodeFromJsonElement<MutableJsonObject>(it) }
    val oldId = (transient?.get("display_id") as? JsonPrimitive)?.content

    if (id == null) return oldId
    if (oldId != null && !force) return oldId

    val newTransient = transient ?: mutableMapOf()
    newTransient["display_id"] = JsonPrimitive(id)
    this["transient"] = Json.encodeToJsonElement(newTransient)
    return id
}

/**
 * Convenient implementation of [DisplayResult],
 * supposed to be used almost always.
 */
class MimeTypedResult(
    private val mimeData: Map<String, String>,
    var isolatedHtml: Boolean = false,
    override val id: String? = null
) : Map<String, String> by mimeData, DisplayResult {
    override fun toJson(additionalMetadata: JsonObject, overrideId: String?): JsonObject {
        val metadata = HashMap<String, JsonElement>().apply {
            if (isolatedHtml) put("text/html", Json.encodeToJsonElement(mapOf("isolated" to true)))
            additionalMetadata.forEach { key, value ->
                put(key, value)
            }
        }

        val result: MutableJsonObject = hashMapOf(
            "data" to Json.encodeToJsonElement(mimeData),
            "metadata" to Json.encodeToJsonElement(metadata)
        )
        result.setDisplayId(overrideId ?: id)
        return Json.encodeToJsonElement(result) as JsonObject
    }
}

// Convenience methods for displaying results
@Suppress("unused", "FunctionName")
fun MIME(vararg mimeToData: Pair<String, String>): MimeTypedResult = mimeResult(*mimeToData)

@Suppress("unused", "FunctionName")
fun HTML(text: String, isolated: Boolean = false) = htmlResult(text, isolated)

fun mimeResult(vararg mimeToData: Pair<String, String>): MimeTypedResult = MimeTypedResult(mapOf(*mimeToData))
fun textResult(text: String): MimeTypedResult = mimeResult("text/plain" to text)
fun htmlResult(text: String, isolated: Boolean = false) = mimeResult("text/html" to text).also { it.isolatedHtml = isolated }

data class HtmlData(val style: String, val body: String, val script: String) {
    override fun toString(): String {
        return toString(null)
    }

    @Language("html")
    fun toString(colorScheme: ColorScheme?): String = """
        <html${if (colorScheme == ColorScheme.DARK) " theme='dark'" else ""}>
        <head>
            <style type="text/css">
                $style
            </style>
        </head>
        <body>
            $body
        </body>
        <script>
            $script
        </script>
        </html>
    """.trimIndent()

    fun toSimpleHtml(colorScheme: ColorScheme?, isolated: Boolean = false): MimeTypedResult = HTML(toString(colorScheme), isolated)
    fun toIFrame(colorScheme: ColorScheme?): MimeTypedResult {
        @Suppress("CssUnresolvedCustomProperty")
        @Language("css")
        val styleData = HtmlData(
            """
                :root {
                    --scroll-bg: #f5f5f5;
                    --scroll-fg: #b3b3b3;
                }
                :root[theme="dark"], :root [data-jp-theme-light="false"]{
                    --scroll-bg: #3c3c3c;
                    --scroll-fg: #97e1fb;
                }
                body {
                    scrollbar-color: var(--scroll-fg) var(--scroll-bg);
                }
                body::-webkit-scrollbar {
                    width: 10px; /* Mostly for vertical scrollbars */
                    height: 10px; /* Mostly for horizontal scrollbars */
                }
                body::-webkit-scrollbar-thumb {
                    background-color: var(--scroll-fg);
                }
                body::-webkit-scrollbar-track {
                    background-color: var(--scroll-bg);
                }
            """.trimIndent(),
            "",
            ""
        )

        val wholeData = this + styleData
        val text = wholeData.toString(colorScheme)

        val id = "iframe_out_${iframeCounter.incrementAndGet()}"
        val fName = "resize_$id"
        val cleanText = text.replace("\"", "&quot;")

        @Language("html")
        val iFramedText = """
            <iframe onload="$fName()" style="width:100%;" class="result_container" id="$id" frameBorder="0" srcdoc="$cleanText"></iframe>
            <script>
                function $fName() {
                    let el = document.getElementById("$id");
                    let h = el.contentWindow.document.body.scrollHeight;
                    el.height = h === 0 ? 0 : h + 41;
                }
            </script>
        """.trimIndent()
        return htmlResult(iFramedText, false)
    }

    operator fun plus(other: HtmlData): HtmlData =
        HtmlData(
            style + "\n" + other.style,
            body + "\n" + other.body,
            script + "\n" + other.script,
        )

    companion object {
        private val iframeCounter = AtomicLong()
    }
}

/**
 * Renders HTML as iframe in Kotlin Notebook or simply in other clients
 *
 * @param data
 */
fun Notebook.renderHtmlAsIFrameIfNeeded(data: HtmlData): MimeTypedResult {
    return if (jupyterClientType == JupyterClientType.KOTLIN_NOTEBOOK) {
        data.toIFrame(currentColorScheme)
    } else {
        data.toSimpleHtml(currentColorScheme)
    }
}
