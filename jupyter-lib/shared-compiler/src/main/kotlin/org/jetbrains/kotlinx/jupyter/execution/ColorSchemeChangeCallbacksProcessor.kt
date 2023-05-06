package org.jetbrains.kotlinx.jupyter.execution

import org.jetbrains.kotlinx.jupyter.api.ExtensionsProcessor
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorSchemeChangedCallback

interface ColorSchemeChangeCallbacksProcessor : ExtensionsProcessor<ColorSchemeChangedCallback> {
    fun runCallbacks()

    fun schemeChanged(newScheme: ColorScheme)
}
