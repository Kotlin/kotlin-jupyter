package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.TextRenderer
import org.jetbrains.kotlinx.jupyter.api.TextRendererWithPriority
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.rethrowAsLibraryException
import org.jetbrains.kotlinx.jupyter.util.PriorityList

class TextRenderersProcessorImpl : TextRenderersProcessorWithPreventingRecursion {
    private val renderers = PriorityList<TextRenderer>()
    private val cache = mutableListOf<CacheEntry>()

    override fun register(renderer: TextRenderer, priority: Int) {
        renderers.add(renderer, priority)
    }

    override fun remove(renderer: TextRenderer) {
        renderers.remove(renderer)
    }

    override fun registeredRenderers(): List<TextRendererWithPriority> {
        return renderers.elementsWithPriority().map { TextRendererWithPriority(it.first, it.second) }
    }

    override fun render(value: Any?): String {
        val cacheIndex = cache.indexOfFirst { it.k === value }
        if (cacheIndex != -1) {
            return cache[cacheIndex].v ?: "<recursion prevented>"
        }

        cache.add(CacheEntry(value))
        val cacheEntry = cache.last()
        fun cached(rendered: String): String {
            cacheEntry.v = rendered
            return rendered
        }

        for (renderer in renderers) {
            val res = rethrowAsLibraryException(LibraryProblemPart.TEXT_RENDERERS) {
                renderer.render(this, value)
            }
            if (res != null) return cached(res)
        }
        return cached(value.toString())
    }

    override fun renderPreventingRecursion(value: Any?): String {
        val res = try {
            cache.clear()
            render(value)
        } finally {
            cache.clear()
        }
        return res
    }

    private data class CacheEntry(
        var k: Any?,
        var v: String? = null,
    )
}
