package org.jetbrains.kotlinx.jupyter.api.graphs.labels

import kotlin.reflect.KProperty1

/**
 * Convenience class for creating [PropObjectLabel] if only fixed subset
 * of properties [propertiesToRender] should be rendered
 */
class FilteringPropObjectLabel<T : Any>(
    value: T,
    override val mainText: String = value.toString(),
    private val propertiesToRender: Collection<String> = emptyList(),
) : PropObjectLabel<T>(value) {
    override fun shouldRenderProperty(prop: KProperty1<out T, *>): Boolean = prop.name in propertiesToRender
}
