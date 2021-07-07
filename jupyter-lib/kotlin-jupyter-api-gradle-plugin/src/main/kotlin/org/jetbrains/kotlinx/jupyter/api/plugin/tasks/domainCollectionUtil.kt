package org.jetbrains.kotlinx.jupyter.api.plugin.tasks

import org.gradle.api.NamedDomainObjectCollection

fun <T> NamedDomainObjectCollection<T>.whenAdded(condition: (T) -> Boolean, action: (T) -> Unit) {
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
