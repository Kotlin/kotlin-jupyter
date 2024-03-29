package org.jetbrains.kotlinx.jupyter.exceptions

import kotlinx.serialization.json.JsonObject
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException

open class ReplException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    open fun getAdditionalInfoJson(): JsonObject? = null

    open fun render(): String? =
        buildString {
            appendLine(message)
            val cause = cause
            if (cause != null) {
                when (cause) {
                    is InvocationTargetException -> appendLine(cause.targetException.toString())
                    is ReplCompilerException -> {
                        appendLine("Error compiling code:")
                        appendLine(cause.failedCode)
                        cause.errorResult?.let { errors ->
                            appendLine("\nErrors:")
                            appendLine(errors.getErrors())
                            appendLine()
                        }
                    }
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

fun Throwable.renderException(): String {
    return when (this) {
        is ReplException -> render().orEmpty()
        else -> messageAndStackTrace()
    }
}
