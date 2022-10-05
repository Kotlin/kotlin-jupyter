package org.jetbrains.kotlinx.jupyter.execution

import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorSchemeChangedCallback

interface ColorSchemeChangeCallbacksProcessor {
    fun runCallbacks()

    fun schemeChanged(newScheme: ColorScheme)

    fun register(callback: ColorSchemeChangedCallback)
}
