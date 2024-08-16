package org.jetbrains.kotlinx.jupyter.exceptions

import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCount

/**
 * Class wrapping metadata for locating the source of the error in the user's notebook.
 */
class ErrorLocation(val jupyterRequestCount: Int, val lineNumber: Int)

/**
 * Errors resulting from evaluating the user's REPL code.
 */
class ReplEvalRuntimeException(
    fileExtension: String,
    scriptFqnToJupyterExecutionCount: Map<String, ExecutionCount>,
    message: String,
    cause: Throwable? = null,
) : ReplException(message, cause) {
    // List of cell error locations for each line in the stacktrace. I.e.
    // each index matches the current index in `Exception.stacktrace`
    // There is only an entry if we can locate the cell and line number
    // the exception line is referring to, otherwise it is null.
    val cellErrorLocations: List<ErrorLocation?> =
        if (cause != null) {
            // Possible patterns we need to look out for:
            // - Top-level cell code: `at Line_4_jupyter.<init>(Line_4.jupyter.kts:7)`
            // - Method in cell: `at Line_5_jupyter.methodName(Line_5.jupyter.kts:7)`
            // - Class inside cell: `at Line_7_jupyter$ClassName.<init>(Line_7.jupyter.kts:12)`
            // - Lambda defined in cell: `at Line_4_jupyter$callback$1.invoke(Line_4.jupyter.kts:2)`
            val pattern = "(?<scriptFQN>Line_\\d+_jupyter).*\\(Line_\\d+.$fileExtension:(?<lineNumber>\\d+)\\)".toRegex()
            cause.stackTrace.map { stackLine: StackTraceElement ->
                val line = stackLine.toString()
                pattern.find(line)?.let { match: MatchResult ->
                    val scriptFQName: String? = match.groups["scriptFQN"]?.value
                    val requestCount: Int? = scriptFqnToJupyterExecutionCount[scriptFQName]?.value
                    val lineNumber: Int? = match.groups["lineNumber"]?.value?.toInt()
                    if (requestCount != null && lineNumber != null) {
                        ErrorLocation(requestCount, lineNumber)
                    } else {
                        null
                    }
                }
            }
        } else {
            emptyList()
        }

    override fun render(): String {
        return cause?.messageAndStackTrace() ?: messageAndStackTrace()
    }
}
