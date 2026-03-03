package org.jetbrains.kotlinx.jupyter.exceptions

import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.api.exceptions.renderException
import org.jetbrains.kotlinx.jupyter.repl.CellErrorMetaData
import org.jetbrains.kotlinx.jupyter.util.trimEmptyLines

/**
 * Class wrapping metadata for locating the source of the error in the user's notebook.
 *
 * @param jupyterRequestCount User visible request count
 * @param lineNumber line number as reported in the stack trace.
 * @param visibleSourceLines Since the compiler might inject invisible code into the cell,
 * [lineNumber] can sometimes point to a line outside the visible range. [visibleSourceLines] tracks
 * the visible limit. Note, a compiler plugin might inject code in the middle of a user's code.
 * If this happens, there is no way to detect it, so for now, we ignore the possibility.
 */
class ErrorLocation(
    val jupyterRequestCount: Int,
    val lineNumber: Int,
    val visibleSourceLines: Int,
)

/**
 * Thrown if the user's REPL code threw an exception at runtime.
 */
class ReplEvalRuntimeException(
    fileExtension: String,
    scriptFqnToJupyterExecutionCount: Map<String, CellErrorMetaData>,
    message: String,
    cause: Throwable? = null,
) : ReplException(message, cause) {
    override val jupyterException: Throwable get() = this.cause!!

    // List of cell error locations for each line in the stacktrace.
    // I.e., each index matches the current line index in `Exception.stackTraceToString()`
    // There is only an entry if we can locate the cell and line number
    // the exception line is referring to, otherwise it is null.
    private val cellErrorLocations: List<ErrorLocation?> =
        if (cause != null) {
            // Possible patterns we need to look out for:
            // - Top-level cell code: `at Line_4_jupyter.<init>(Line_4.jupyter.kts:7)`
            // - Method in cell: `at Line_5_jupyter.methodName(Line_5.jupyter.kts:7)`
            // - Class inside cell: `at Line_7_jupyter$ClassName.<init>(Line_7.jupyter.kts:12)`
            // - Lambda defined in cell: `at Line_4_jupyter$callback$1.invoke(Line_4.jupyter.kts:2)`
            val pattern = "(?<scriptFQN>Line_\\d+_jupyter).*\\(Line_\\d+.$fileExtension:(?<lineNumber>\\d+)\\)".toRegex()

            // Search all exception causes all the way down for errors in the executed code.
            cause.traceback.mapIndexed { i, line ->
                pattern.find(line)?.let { match: MatchResult ->
                    val scriptFQName: String? = match.groups["scriptFQN"]?.value
                    val data = scriptFqnToJupyterExecutionCount[scriptFQName]
                    val requestCount: Int? = data?.executionCount?.value
                    val lineNumber: Int? = match.groups["lineNumber"]?.value?.toInt()
                    val maxLines = data?.linesOfUserSourceCode ?: 0
                    if (requestCount != null && lineNumber != null) {
                        ErrorLocation(requestCount, lineNumber, maxLines)
                    } else {
                        null
                    }
                }
            }
        } else {
            emptyList()
        }

    override fun render(): String = jupyterException.renderException()

    override val traceback get(): List<String> {
        val userException = jupyterException
        return buildString {
            // IntelliJ will check for the first occurrence of "\n<exceptionType>: <exceptionMessage>"`
            // (note the newline) and hide everything above it under a fold named "Stacktrace..."
            //
            // So by printing the full stacktrace as the first thing, we ensure that it gets
            // hidden by default and only the top exception type/message + cell information
            // is shown to the user.
            val tracebackLines =
                userException.traceback.mapIndexedNotNull { index, line ->
                    // Account for the first line being the exception type + message and the last (empty) line
                    // being created by stackTraceToString()
                    val errorMetadata = if (index >= 0 && index < cellErrorLocations.size) cellErrorLocations[index] else null
                    val tracebackLine =
                        errorMetadata?.let { metadata ->
                            line +
                                when (metadata.lineNumber <= metadata.visibleSourceLines) {
                                    true -> " at Cell In[${metadata.jupyterRequestCount}], line ${metadata.lineNumber}"
                                    false -> " at Cell In[${metadata.jupyterRequestCount}]"
                                }
                        } ?: line
                    tracebackLine.takeIf { it.isNotEmpty() }
                }

            if (tracebackLines.isNotEmpty()) {
                for (line in tracebackLines) {
                    appendLine(line)
                }
                appendLine()
            }

            appendLine(userException.header)
            val topError = cellErrorLocations.firstOrNull { it != null }
            if (topError != null) {
                if (topError.lineNumber > topError.visibleSourceLines) {
                    appendLine("at Cell In[${topError.jupyterRequestCount}]")
                } else {
                    appendLine("at Cell In[${topError.jupyterRequestCount}], line ${topError.lineNumber}")
                }
            }
        }.lines().trimEmptyLines()
    }

    private val Throwable.traceback get() =
        when (this) {
            is ReplException -> traceback
            else -> stackTraceToString().lines()
        }

    private val Throwable.header: String get() {
        return when (this) {
            is ReplException -> customHeader
            else -> null
        } ?: "${javaClass.canonicalName}: $message"
    }
}
