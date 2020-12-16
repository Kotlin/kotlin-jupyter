package org.jetbrains.kotlin.jupyter.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

interface Renderable {
    fun render(notebook: Notebook<*>): DisplayResult
}

interface DisplayResult : Renderable {
    val id: String? get() = null

    fun toJson(additionalMetadata: JsonObject = JsonObject(mapOf())): JsonObject

    override fun render(notebook: Notebook<*>) = this
}

interface DisplayResultWithCell : DisplayResult {
    val cell: CodeCell
}

interface DisplayContainer {
    fun getAll(): List<DisplayResultWithCell>
    fun getById(id: String?): List<DisplayResultWithCell>
}

typealias MutableJsonObject = MutableMap<String, JsonElement>

@Suppress("unused")
fun DisplayResult?.toJson(): JsonObject {
    if (this != null) return this.toJson()
    return Json.encodeToJsonElement(mapOf("data" to null, "metadata" to JsonObject(mapOf()))) as JsonObject
}

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

class MimeTypedResult(
    private val mimeData: Map<String, String>,
    var isolatedHtml: Boolean = false,
    override val id: String? = null
) : Map<String, String> by mimeData, DisplayResult {
    override fun toJson(additionalMetadata: JsonObject): JsonObject {
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
        result.setDisplayId(id)
        return Json.encodeToJsonElement(result) as JsonObject
    }
}

@Suppress("unused", "FunctionName")
fun MIME(vararg mimeToData: Pair<String, String>): MimeTypedResult = mimeResult(*mimeToData)

@Suppress("unused", "FunctionName")
fun HTML(text: String, isolated: Boolean = false) = htmlResult(text, isolated)

fun mimeResult(vararg mimeToData: Pair<String, String>): MimeTypedResult = MimeTypedResult(mapOf(*mimeToData))
fun textResult(text: String): MimeTypedResult = mimeResult("text/plain" to text)
fun htmlResult(text: String, isolated: Boolean = false) = mimeResult("text/html" to text).also { it.isolatedHtml = isolated }
