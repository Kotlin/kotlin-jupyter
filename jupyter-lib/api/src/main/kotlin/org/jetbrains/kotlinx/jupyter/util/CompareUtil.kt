package org.jetbrains.kotlinx.jupyter.util

import kotlin.reflect.KProperty1

fun <T : Comparable<T>> compareNullable(
    s1: T?,
    s2: T?,
): Int =
    if (s1 == null) {
        if (s2 == null) {
            0
        } else {
            -1
        }
    } else {
        if (s2 == null) {
            1
        } else {
            s1.compareTo(s2)
        }
    }

fun <T : Comparable<T>, P : Comparable<P>> T.compareProperty(
    other: T,
    property: KProperty1<T, P?>,
): Int {
    val thisP = property.get(this)
    val otherP = property.get(other)
    return compareNullable(thisP, otherP)
}

fun <T : Comparable<T>, P : Comparable<P>> T.compareByProperties(
    other: T,
    vararg properties: KProperty1<T, P?>,
): Int {
    for (prop in properties) {
        val comparison = compareProperty(other, prop)
        if (comparison != 0) return comparison
    }
    return 0
}
