package org.jetbrains.kotlinx.jupyter.api.plugin.util

import org.gradle.api.NamedDomainObjectCollection

internal fun <T : Any> NamedDomainObjectCollection<T>.whenAdded(condition: (T) -> Boolean, action: (T) -> Unit) {
    val element = find(condition)
    if (element != null) {
        action(element)
        return
    }
    whenObjectAdded {
        val addedElement = this
        if (condition(addedElement)) action(addedElement)
    }
}
