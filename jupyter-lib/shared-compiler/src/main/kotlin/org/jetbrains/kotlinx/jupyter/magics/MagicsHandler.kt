package org.jetbrains.kotlinx.jupyter.magics

interface MagicsHandler {
    fun handle(magicText: String, tryIgnoreErrors: Boolean, parseOnly: Boolean)
}
