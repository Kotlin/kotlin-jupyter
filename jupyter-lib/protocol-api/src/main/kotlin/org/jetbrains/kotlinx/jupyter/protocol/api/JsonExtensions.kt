package org.jetbrains.kotlinx.jupyter.protocol.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val emptyJsonObject = JsonObject(mapOf())

@Suppress("UnusedReceiverParameter")
val Json.EMPTY get() = emptyJsonObject
