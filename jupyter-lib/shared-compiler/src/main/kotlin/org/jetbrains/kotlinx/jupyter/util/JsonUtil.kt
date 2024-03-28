package org.jetbrains.kotlinx.jupyter.util

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.kotlinx.jupyter.protocol.MessageFormat

@JvmName("jsonObjectForString")
fun jsonObject(vararg namedValues: Pair<String, String?>): JsonObject = MessageFormat.encodeToJsonElement(hashMapOf(*namedValues)) as JsonObject

@JvmName("jsonObjectForPrimitive")
fun jsonObject(vararg namedValues: Pair<String, JsonElement>): JsonObject = MessageFormat.encodeToJsonElement(hashMapOf(*namedValues)) as JsonObject

fun jsonObject(namedValues: Iterable<Pair<String, Any?>>): JsonObject = buildJsonObject {
    namedValues.forEach { (key, value) -> put(key, MessageFormat.encodeToJsonElement(value)) }
}

internal operator fun JsonObject?.get(key: String) = this?.get(key)
