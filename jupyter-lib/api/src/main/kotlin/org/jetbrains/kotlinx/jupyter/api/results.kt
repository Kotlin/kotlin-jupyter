package org.jetbrains.kotlinx.jupyter.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

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
    fun toJson(additionalMetadata: JsonObject = JsonObject(mapOf()), overrideId: String? = null): JsonObject

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
    if (this != null) return this.toJson()
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
