package org.jetbrains.kotlinx.jupyter.exceptions

fun Throwable.causesSequence(): Sequence<Throwable> {
    val cache = mutableSetOf<Throwable>()
    return generateSequence(this) {
        val t = it.cause
        if (t != null && cache.add(t)) t else null
    }
}

fun Throwable.isInterruptedException(): Boolean {
    return causesSequence().any {
        it is InterruptedException || it is ThreadDeath
    }
}

/**
 * Runs all [ExceptionMerger.catchIndependently] blocks, even if there was an exception in one or more of them.
 * If there were exceptions, the first one is thrown, and the others are added as suppressed to it.
 */
inline fun mergeExceptions(actions: ExceptionMerger.() -> Unit): Unit =
    ExceptionMerger().apply(actions).finalize()

class ExceptionMerger {
    private var error: Throwable? = null

    /** Is `true` if there was an exception in at least one [catchIndependently] block so far. */
    val failing: Boolean get () = error != null

    inline fun catchIndependently(action: () -> Unit) {
        try {
            action()
        } catch (e: Throwable) {
            addError(e)
        }
    }

    fun addError(e: Throwable) {
        if (error == null) {
            error = e
        } else {
            error!!.addSuppressed(e)
        }
    }

    fun finalize() {
        error?.let { throw it }
    }
}
