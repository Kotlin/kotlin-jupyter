package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlinx.jupyter.MutablePair
import org.jetbrains.kotlinx.jupyter.api.ProcessingPriority
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme
import org.jetbrains.kotlinx.jupyter.api.libraries.ColorSchemeChangedCallback
import org.jetbrains.kotlinx.jupyter.execution.ColorSchemeChangeCallbacksProcessor
import org.jetbrains.kotlinx.jupyter.util.PriorityList

class ColorSchemeChangeCallbacksProcessorImpl : ColorSchemeChangeCallbacksProcessor {
    private var currentScheme: ColorScheme? = null
    private val callbacks = PriorityList<MutablePair<ColorSchemeChangedCallback, ColorScheme?>>()

    override fun runCallbacks() {
        currentScheme?.let { schemeChanged(it) }
    }

    override fun schemeChanged(newScheme: ColorScheme) {
        currentScheme = newScheme
        for (p in callbacks) {
            if (newScheme != p.second) {
                p.second = newScheme
                p.first.schemeChanged(newScheme)
            }
        }
    }

    override fun register(extension: ColorSchemeChangedCallback) {
        register(extension, ProcessingPriority.DEFAULT)
    }

    override fun register(extension: ColorSchemeChangedCallback, priority: Int) {
        val scheme = currentScheme
        callbacks.add(MutablePair(extension, scheme), priority)
        if (scheme != null) {
            extension.schemeChanged(scheme)
        }
    }

    override fun unregister(extension: ColorSchemeChangedCallback) {
        callbacks.removeIf { it.first === extension }
    }

    override fun unregisterAll() {
        callbacks.clear()
    }

    override fun registeredExtensions(): Collection<ColorSchemeChangedCallback> {
        return callbacks.elements().map { it.first }
    }

    override fun registeredExtensionsWithPriority(): List<Pair<ColorSchemeChangedCallback, Int>> {
        return callbacks.elementsWithPriority().map { (p, priority) -> p.first to priority }
    }
}
