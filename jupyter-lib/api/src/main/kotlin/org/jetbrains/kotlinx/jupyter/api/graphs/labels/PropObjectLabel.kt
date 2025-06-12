package org.jetbrains.kotlinx.jupyter.api.graphs.labels

import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Renders [value] object with its properties for
 * those [shouldRenderProperty] returns `true`
 */
open class PropObjectLabel<T : Any>(
    val value: T,
) : RecordTableLabel() {
    override val mainText get() = value.toString()

    override val properties: Collection<Iterable<String>> get() {
        val kClass = value::class

        return kClass.memberProperties
            .filter(::shouldRenderProperty)
            .map { prop ->
                @Suppress("UNCHECKED_CAST")
                prop as KProperty1<T, *>
                prop.isAccessible = true
                listOf(prop.name, prop.invoke(value).toString())
            }
    }

    open fun shouldRenderProperty(prop: KProperty1<out T, *>) = true
}
