package org.jetbrains.kotlinx.jupyter.api.exceptions

import kotlinx.serialization.json.JsonObject
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException

/**
 * Base class for all kinds of exceptions that may happen in REPL
 */
open class ReplException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /**
     * Type (in JVM sense) and message of this exception that will end up as `ename` and `evalue`
     * in the Jupyter error reply message
     */
    open val jupyterException: Throwable get() = this

    /**
     * If this exception is a cause of runtime failure, this header will be specified after
     * the exception traceback, if not null.
     * If null, the default header will be specified, which is "<type>: <message>"
     */
    open val customHeader: String? get() = null

    /**
     * Traceback (in the Jupyter sense) of this exception
     */
    open val traceback: List<String> get() = messageAndStackTrace(false).lines()

    /**
     * This JSON may contain some extra metadata for a Jupyter client, i.e.,
     * a line of code to highlight as an error's cause
     */
    open fun getAdditionalInfoJson(): JsonObject? = null

    /**
     * Sometimes errors should be printed to the error stream instead of being returned as an error reply.
     * I.e., in cases when they appear in the library integration and not in the user code.
     * In such cases this method is called to calculate a string representation of them
     */
    open fun render(): String? =
        buildString {
            appendLine(message)
            val cause = cause
            if (cause != null) {
                when (cause) {
                    is InvocationTargetException -> appendLine(cause.targetException.toString())
                    is ReplExceptionCause -> cause.run { appendCause() }
                    else -> appendLine(cause.toString())
                }
            }
            appendLine(messageAndStackTrace(false))
        }
}

fun Throwable.messageAndStackTrace(withMessage: Boolean = true): String {
    val writer = StringWriter()
    val printer = PrintWriter(writer)
    if (withMessage) printer.println(message)
    printStackTrace(printer)
    printer.flush()
    return writer.toString()
}

fun Throwable.renderException(): String =
    when (this) {
        is ReplException -> render().orEmpty()
        else -> messageAndStackTrace()
    }
