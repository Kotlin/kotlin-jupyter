package org.jetbrains.kotlinx.jupyter.protocol.exceptions

/**
 * Runs all [ExceptionMerger.catchIndependently] blocks, even if there was an exception in one or more of them.
 * If there were exceptions, the first one is thrown, and the others are added as suppressed to it.
 * If there was an exception outside of [ExceptionMerger.catchIndependently],
 * it is also added as suppressed to any previous exception, which is thrown.
 */
inline fun mergeExceptions(actions: ExceptionMerger.() -> Unit) {
    val exceptionMerger = ExceptionMerger()
    var throwable: Throwable? = null
    try {
        exceptionMerger.run(actions)
    } catch (e: Throwable) {
        throwable = e
        throw e
    } finally {
        throwable?.let { exceptionMerger.addError(it) }
        exceptionMerger.finalize()
    }
}

class ExceptionMerger {
    private var error: Throwable? = null

    /** Is `true` if there was an exception in at least one [catchIndependently] block so far. */
    val failing: Boolean get() = error != null

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

/**
 * Like try-finally block, but if both [action] and [finally] blocks throw exceptions,
 * the latter is added to the former as suppressed, instead of the former being ignored.
 */
inline fun <R> tryFinally(
    action: () -> R,
    finally: () -> Unit,
): R {
    var result: R? = null
    mergeExceptions {
        try {
            catchIndependently { result = action() }
        } finally {
            // the exception can never leave the `catchIndependently` block above,
            // but a non-local return inside action() can, that's why we need this `finally`
            catchIndependently { finally() }
        }
    }
    @Suppress("UNCHECKED_CAST")
    return result as R // if `result` wasn't assigned due to an exception or a non-local return, we wouldn't get here
}
