package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.SessionOptions

class SessionOptionsImpl : SessionOptions {
    override var resolveSources: Boolean = false
    override var resolveMpp: Boolean = false
    override var serializeScriptData: Boolean = false
}
