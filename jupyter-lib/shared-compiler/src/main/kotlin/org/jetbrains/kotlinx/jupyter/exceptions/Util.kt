package org.jetbrains.kotlinx.jupyter.exceptions

fun Throwable.causesSequence(): Sequence<Throwable> {
    val cache = mutableSetOf<Throwable>()
    return generateSequence(this) {
        val t = it.cause
        if (t != null && cache.add(t)) t else null
    }
}

fun Throwable.isInterruptedException(): Boolean =
    causesSequence().any {
        it is InterruptedException || it is ThreadDeath
    }
