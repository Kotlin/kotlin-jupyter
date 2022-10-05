package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorSchemeChangedCallback
import org.jetbrains.kotlinx.jupyter.execution.ColorSchemeChangeCallbacksProcessor

class ColorSchemeChangeCallbacksProcessorImpl : ColorSchemeChangeCallbacksProcessor {
    private var currentScheme: ColorScheme? = null
    private val callbacks = mutableMapOf<ColorSchemeChangedCallback, ColorScheme?>()

    override fun runCallbacks() {
        currentScheme?.let { schemeChanged(it) }
    }

    override fun schemeChanged(newScheme: ColorScheme) {
        currentScheme = newScheme
        for ((callback, scheme) in callbacks) {
            if (newScheme != scheme) {
                callbacks[callback] = newScheme
                callback.schemeChanged(newScheme)
            }
        }
    }

    override fun register(callback: ColorSchemeChangedCallback) {
        val scheme = currentScheme
        callbacks[callback] = scheme
        if (scheme != null) {
            callback.schemeChanged(scheme)
        }
    }
}
