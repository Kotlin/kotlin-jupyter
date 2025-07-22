package org.jetbrains.kotlinx.jupyter.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import org.jetbrains.kotlinx.jupyter.api.outputs.IsolatedHtmlMarker
import org.jetbrains.kotlinx.jupyter.api.outputs.MetadataModifier
import org.jetbrains.kotlinx.jupyter.api.outputs.hasModifier
import org.jetbrains.kotlinx.jupyter.api.outputs.isExpandedJson
import org.jetbrains.kotlinx.jupyter.api.outputs.isIsolatedHtml
import org.jetbrains.kotlinx.jupyter.api.outputs.standardMetadataModifiers
import org.jetbrains.kotlinx.jupyter.protocol.api.EMPTY
import org.jetbrains.kotlinx.jupyter.util.escapeForIframe
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
 * Wrapper for in-memory results that also tracks its corresponding mime-type.
 */
data class InMemoryResult(
    val mimeType: String,
    val result: Any?,
)

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
    fun toJson(
        additionalMetadata: JsonObject = Json.EMPTY,
        overrideId: String? = null,
    ): JsonObject

    @Deprecated("Use full version instead", ReplaceWith("toJson(additionalMetadata, null)"))
    fun toJson(additionalMetadata: JsonObject = Json.EMPTY): JsonObject = toJson(additionalMetadata, null)

    /**
     * Renders display result, generally should return `this`
     */
    override fun render(notebook: Notebook): DisplayResult = this
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
fun DisplayResult.withId(id: String) =
    if (id == this.id) {
        this
    } else {
        object : DisplayResult {
            override fun toJson(
                additionalMetadata: JsonObject,
                overrideId: String?,
            ) = this@withId.toJson(additionalMetadata, overrideId ?: id)

            override val id = id
        }
    }

/**
 * Sets display ID to JSON.
 * If ID was not set, sets it to [id] and returns it back
 * If ID was set and [force] is false, just returns old ID
 * If ID was set, [force] is true and [id] is `null`, just returns old ID
 * If ID was set, [force] is true and [id] is not `null`, sets ID to [id] and returns it back
 */
fun MutableJsonObject.setDisplayId(
    id: String? = null,
    force: Boolean = false,
): String? {
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
 * Check if the JSON object contains a `display_id` entry.
 */
fun JsonObject.containsDisplayId(id: String): Boolean {
    val transient: JsonObject? = get("transient") as? JsonObject
    return (transient?.get("display_id") as? JsonPrimitive)?.content == id
}

/**
 * Wrapper for [DisplayResult]s that contain in memory results.
 * This is only applicable to the embedded server.
 *
 * @param inMemoryOutput the in-memory result + its mime-type that tells the client how to render it.
 * @param fallbackResult fallback output the client can use as a placeholder for the in-memory result, if it is
 * no longer available. Like when saving the notebook to disk.
 */
class InMemoryMimeTypedResult(
    val inMemoryOutput: InMemoryResult,
    val fallbackResult: Map<String, JsonElement>,
) : DisplayResult {
    override fun toJson(
        additionalMetadata: JsonObject,
        overrideId: String?,
    ): JsonObject = throw UnsupportedOperationException("This method is not supported for in-memory values")
}

/**
 * Convenient implementation of [DisplayResult],
 * supposed to be used almost always.
 */
class MimeTypedResult(
    private val mimeData: Map<String, String>,
    isolatedHtml: Boolean = false,
    id: String? = null,
) : MimeTypedResultEx(
        Json.encodeToJsonElement(mimeData),
        id,
        standardMetadataModifiers(isolatedHtml = isolatedHtml),
    ),
    Map<String, String> by mimeData

open class MimeTypedResultEx(
    private val mimeData: JsonElement,
    override val id: String? = null,
    metadataModifiers: List<MetadataModifier> = emptyList(),
) : DisplayResult {
    private val metadataModifiers: MutableSet<MetadataModifier> = mutableSetOf()

    fun addMetadataModifier(metadataModifier: MetadataModifier) {
        metadataModifiers.add(metadataModifier)
    }

    fun removeMetadataModifier(metadataModifier: MetadataModifier) {
        metadataModifiers.remove(metadataModifier)
    }

    fun hasMetadataModifiers(predicate: (MetadataModifier) -> Boolean): Boolean = metadataModifiers.any(predicate)

    init {
        for (metadataModifier in metadataModifiers) {
            addMetadataModifier(metadataModifier)
        }
    }

    @Deprecated(
        "Use primary constructor instead",
        replaceWith =
            ReplaceWith(
                "MimeTypedResultEx(mimeData, id, standardMetadataModifiers(isolatedHtml = isolatedHtml))",
                "org.jetbrains.kotlinx.jupyter.api.outputs.standardMetadataModifiers",
            ),
    )
    constructor(
        mimeData: JsonElement,
        isolatedHtml: Boolean = false,
        id: String? = null,
    ) : this(
        mimeData,
        id,
        standardMetadataModifiers(isolatedHtml = isolatedHtml),
    )

    @Deprecated(
        "Use metadata modifiers instead",
        replaceWith = ReplaceWith("isIsolated", "org.jetbrains.kotlinx.jupyter.api.outputs.isIsolated"),
    )
    @Suppress("unused") // Left for binary compatibility
    var isolatedHtml by hasModifier(IsolatedHtmlMarker)

    override fun toJson(
        additionalMetadata: JsonObject,
        overrideId: String?,
    ): JsonObject {
        val metadata =
            buildJsonObject {
                for (modifier in metadataModifiers) {
                    with(modifier) { modifyMetadata() }
                }
                additionalMetadata.forEach { key, value ->
                    put(key, value)
                }
            }

        val result: MutableJsonObject =
            hashMapOf(
                "data" to mimeData,
                "metadata" to metadata,
            )
        result.setDisplayId(overrideId ?: id)
        return Json.encodeToJsonElement(result) as JsonObject
    }

    override fun toString(): String {
        val json: JsonElement = toJson(Json.EMPTY, null)
        return jsonPrettyPrinter.encodeToString(JsonElement.serializer(), json)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is MimeTypedResultEx) return false
        return toJson(Json.EMPTY, null) == other.toJson(Json.EMPTY, null)
    }

    override fun hashCode(): Int {
        var result = mimeData.hashCode()
        result = 31 * result + isIsolatedHtml.hashCode()
        result = 31 * result + isExpandedJson.hashCode()
        result = 31 * result + (id?.hashCode() ?: 0)
        return result
    }
}

// Convenience methods for displaying results
@Suppress("unused", "FunctionName")
fun MIME(vararg mimeToData: Pair<String, String>): MimeTypedResult = mimeResult(*mimeToData)

@Suppress("unused", "FunctionName")
fun HTML(
    text: String,
    isolated: Boolean = false,
) = htmlResult(text, isolated)

private val jsonPrettyPrinter = Json { prettyPrint = true }

@Suppress("FunctionName")
fun JSON(
    json: JsonElement,
    isolated: Boolean = false,
    expanded: Boolean = true,
) = MimeTypedResultEx(
    buildJsonObject {
        val encodedJson = jsonPrettyPrinter.encodeToString(JsonElement.serializer(), json)
        put(MimeTypes.JSON, json)
        put(MimeTypes.PLAIN_TEXT, JsonPrimitive(encodedJson))
        val backticks = "`".repeat(markdownCodeBlockBackticksCount(encodedJson))
        put(MimeTypes.MARKDOWN, JsonPrimitive("${backticks}json\n$encodedJson\n$backticks"))
    },
    null,
    standardMetadataModifiers(
        isolatedHtml = isolated,
        expandedJson = expanded,
    ),
)

private val markdownBackticksRegex = Regex("`{3,}")

/** Return minimum number of backticks that are required to wrap [code] in Markdown code block without escaping it. */
private fun markdownCodeBlockBackticksCount(code: String): Int =
    markdownBackticksRegex.findAll(code).maxOfOrNull { it.value.length + 1 } ?: 3

@Suppress("unused", "FunctionName")
fun JSON(
    jsonText: String,
    isolated: Boolean = false,
    expanded: Boolean = true,
) = JSON(Json.parseToJsonElement(jsonText), isolated, expanded)

fun mimeResult(vararg mimeToData: Pair<String, String>): MimeTypedResult = MimeTypedResult(mapOf(*mimeToData))

fun textResult(text: String): MimeTypedResult = mimeResult(MimeTypes.PLAIN_TEXT to text)

fun htmlResult(
    text: String,
    isolated: Boolean = false,
) = MimeTypedResult(mapOf(MimeTypes.HTML to text), isolated)

data class HtmlData(
    val style: String,
    val body: String,
    val script: String,
) {
    override fun toString(): String = toString(null)

    @Language("html")
    fun toString(colorScheme: ColorScheme?): String =
        """
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

    fun toSimpleHtml(
        colorScheme: ColorScheme?,
        isolated: Boolean = false,
    ): MimeTypedResult = HTML(toString(colorScheme), isolated)

    fun toIFrame(colorScheme: ColorScheme?): MimeTypedResult {
        val iFramedText = generateIframePlaneText(colorScheme)
        return htmlResult(iFramedText, false)
    }

    fun generateIframePlaneText(colorScheme: ColorScheme?): String {
        @Suppress("CssUnresolvedCustomProperty")
        @Language("css")
        val styleData =
            HtmlData(
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
                "",
            )

        val wholeData = this + styleData
        val text = wholeData.toString(colorScheme)

        val id = "iframe_out_${iframeCounter.incrementAndGet()}"
        val fName = "resize_$id"
        val cleanText = text.escapeForIframe()

        @Language("html")
        val iFramedText =
            """
            <iframe onload="o_$fName()" style="width:100%;" class="result_container" id="$id" frameBorder="0" srcdoc="$cleanText"></iframe>
            <script>
                function o_$fName() {
                    let elem = document.getElementById("$id");
                    $fName(elem);
                    setInterval($fName, 5000, elem);
                }
                function $fName(el) {
                    let h = el.contentWindow.document.body.scrollHeight;
                    el.height = h === 0 ? 0 : h + 41;
                }
            </script>
            """.trimIndent()

        return iFramedText
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
fun Notebook.renderHtmlAsIFrameIfNeeded(data: HtmlData): MimeTypedResult =
    if (jupyterClientType == JupyterClientType.KOTLIN_NOTEBOOK) {
        data.toIFrame(currentColorScheme)
    } else {
        data.toSimpleHtml(currentColorScheme)
    }

object MimeTypes {
    const val HTML = "text/html"
    const val PLAIN_TEXT = "text/plain"
    const val MARKDOWN = "text/markdown"
    const val JSON = "application/json"

    const val PNG = "image/png"
    const val JPEG = "image/jpeg"
    const val SVG = "image/svg+xml"
}

/**
 * Mimetypes for in-memory output.
 */
object InMemoryMimeTypes {
    const val SWING = "application/vnd.idea.swing"
    const val COMPOSE = "application/vnd.idea.compose"

    val allTypes = listOf(SWING, COMPOSE)
}
