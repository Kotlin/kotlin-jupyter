package org.jetbrains.kotlinx.jupyter.api.libraries

enum class ColorScheme {
    LIGHT,
    DARK,
}

fun interface ColorSchemeChangedCallback {
    fun schemeChanged(newScheme: ColorScheme)
}
