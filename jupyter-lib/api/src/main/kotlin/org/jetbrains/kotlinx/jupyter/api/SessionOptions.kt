package org.jetbrains.kotlinx.jupyter.api

interface SessionOptions {
    var resolveSources: Boolean
    var resolveMpp: Boolean
    var serializeScriptData: Boolean
}
