package org.jetbrains.kotlinx.jupyter.exceptions

import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplUnwrappedException
import java.io.PrintWriter

class CompositeReplException(
    private val exceptions: Collection<Throwable>,
    libraryProblemPart: LibraryProblemPart?,
) : ReplException(
        "CompositeException${libraryProblemPart?.message?.let { " in $it" }.orEmpty()}: ${exceptions.size} exceptions occurred.",
    ) {
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
    fun contains(predicate: (Throwable) -> Boolean): Boolean = exceptions.any(predicate)

    /**
     * Returns a list of the causes of the exceptions in the composite exception.
     */
    fun getCauses(): List<Throwable> = exceptions.flatMap { it.getCauses() }.distinct()
}

/**
 * Returns a list of all the causes of a throwable.
 */
fun Throwable.getCauses(): List<Throwable> = generateSequence(cause) { it.cause }.toList()

fun Throwable.throwAsLibraryException(part: LibraryProblemPart): Nothing =
    throw when (this) {
        is ReplUnwrappedException -> this
        else -> ReplLibraryException(part = part, cause = this)
    }

fun Collection<Throwable>.throwLibraryException(part: LibraryProblemPart) {
    when (size) {
        0 -> return
        1 -> first().throwAsLibraryException(part)
        else -> throw CompositeReplException(this, part)
    }
}
