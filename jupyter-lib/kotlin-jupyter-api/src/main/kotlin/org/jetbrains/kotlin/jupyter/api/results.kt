package org.jetbrains.kotlin.jupyter.api

import com.beust.klaxon.JsonObject

interface Renderable {
    fun render(notebook: Notebook<*>): DisplayResult
}

interface DisplayResult: Renderable {
    val id: String? get() = null

    fun toJson(): JsonObject

    override fun render(notebook: Notebook<*>) = this
}

interface DisplayResultWithCell: DisplayResult {
    val cell: CodeCell
}

interface DisplayContainer {
    fun getAll(): List<DisplayResultWithCell>
    fun getById(id: String?): List<DisplayResultWithCell>
}

fun DisplayResult?.toJson(): JsonObject {
    if (this != null) return this.toJson()
    return jsonObject("data" to null, "metadata" to jsonObject())
}

fun JsonObject.setDisplayId(id: String? = null, force: Boolean = false): String? {
    val transient = get("transient") as? JsonObject
    val oldId = transient?.get("display_id") as? String

    if (id == null) return oldId
    if (oldId != null && !force) return oldId

    val newTransient = transient ?: jsonObject()
    newTransient["display_id"] = id
    this["transient"] = newTransient
    return id
}

private fun jsonObject(vararg namedVals: Pair<String, Any?>): JsonObject = JsonObject(hashMapOf(*namedVals))

class MimeTypedResult(
        mimeData: Map<String, String>,
        var isolatedHtml: Boolean = false,
        override val id: String? = null
) : Map<String, String> by mimeData, DisplayResult {
    override fun toJson(): JsonObject {
        val data = JsonObject(this)
        val metadata = if (isolatedHtml) jsonObject("text/html" to jsonObject("isolated" to true)) else jsonObject()
        val result = jsonObject(
                "data" to data,
                "metadata" to metadata
        )
        result.setDisplayId(id)
        return result
    }
}

fun MIME(vararg mimeToData: Pair<String, String>): MimeTypedResult = mimeResult(*mimeToData)
fun HTML(text: String, isolated: Boolean = false) = htmlResult(text, isolated)

fun mimeResult(vararg mimeToData: Pair<String, String>): MimeTypedResult = MimeTypedResult(mapOf(*mimeToData))
fun textResult(text: String): MimeTypedResult = mimeResult("text/plain" to text)
fun htmlResult(text: String, isolated: Boolean = false) = mimeResult("text/html" to text).also { it.isolatedHtml = isolated }
