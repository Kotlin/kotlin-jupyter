package org.jetbrains.kotlinx.jupyter.api.exceptions

/**
 * The kind of exception that renders only its message, no stacktrace or cause
 */
open class ReplMessageOnlyException(
    message: String,
    cause: Throwable? = null,
) : ReplException(message, cause) {
    override val customHeader: String? = ""
    override val traceback: List<String> = listOf(message)

    override fun render(): String = message!!
}
