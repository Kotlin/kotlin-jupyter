package org.jetbrains.kotlinx.jupyter.api.graphs.labels

import kotlin.reflect.KProperty

abstract class HtmlAttributes<out Attr>(
    protected val properties: MutableMap<String, Any> = mutableMapOf(),
) {
    val html: String get() {
        return properties.entries.joinToString(separator = "") { (key, value) -> """ $key="$value"""" }
    }

    abstract fun copy(): Attr

    protected fun <T : Any> attr() = AttributeDelegate<T>()

    class AttributeDelegate<T : Any> {
        operator fun getValue(
            thisRef: HtmlAttributes<*>,
            property: KProperty<*>,
        ): T? {
            @Suppress("UNCHECKED_CAST")
            return thisRef.properties[property.name] as T?
        }

        operator fun setValue(
            thisRef: HtmlAttributes<*>,
            property: KProperty<*>,
            value: T?,
        ) {
            if (value != null) {
                thisRef.properties[property.name] = value
            } else {
                thisRef.properties.remove(property.name)
            }
        }
    }

    abstract class HtmlAttributesCompanion<T : HtmlAttributes<T>> {
        abstract val default: T

        fun build(action: T.() -> Unit): T = default.copy().apply { action() }
    }
}
