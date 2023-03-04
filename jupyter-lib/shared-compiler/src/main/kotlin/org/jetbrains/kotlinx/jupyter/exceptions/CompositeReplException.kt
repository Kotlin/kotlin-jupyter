package org.jetbrains.kotlinx.jupyter.exceptions

import java.io.PrintWriter

class CompositeReplException(
    private val exceptions: Collection<Throwable>,
    libraryProblemPart: LibraryProblemPart?,
) : ReplException("CompositeException${libraryProblemPart?.message?.let { " in $it" }.orEmpty()}: ${exceptions.size} exceptions occurred.") {
    override fun printStackTrace() {
        printStackTrace(System.err)
    }

    override fun printStackTrace(pw: PrintWriter) {
        synchronized(pw) {
            pw.println(message)
            pw.println("-------------------------------")
            exceptions.forEachIndexed { index, throwable ->
                pw.println("Exception $index:")
                throwable.printStackTrace(pw)
                pw.println("-------------------------------")
            }
        }
    }

    /**
     * Returns true if any of the exceptions in the composite exception match the specified predicate.
     */
    fun contains(predicate: (Throwable) -> Boolean): Boolean {
        return exceptions.any(predicate)
    }

    /**
     * Returns a list of the causes of the exceptions in the composite exception.
     */
    fun getCauses(): List<Throwable> {
        return exceptions.flatMap { it.getCauses() }.distinct()
    }
}

/**
 * Returns a list of all the causes of a throwable.
 */
fun Throwable.getCauses(): List<Throwable> {
    return generateSequence(cause) { it.cause }.toList()
}

fun Collection<Throwable>.throwLibraryException(part: LibraryProblemPart) {
    when (size) {
        0 -> return
        1 -> throw ReplLibraryException(part = part, cause = first())
        else -> throw CompositeReplException(this, part)
    }
}
