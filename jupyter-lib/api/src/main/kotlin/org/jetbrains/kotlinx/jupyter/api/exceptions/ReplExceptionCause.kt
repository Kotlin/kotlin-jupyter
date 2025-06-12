package org.jetbrains.kotlinx.jupyter.api.exceptions

/**
 * Exceptions may implement this interface to add the cause
 * while rendered by [ReplException]
 */
interface ReplExceptionCause {
    fun StringBuilder.appendCause()
}
