package org.jetbrains.kotlinx.jupyter.debug

import java.io.File

/**
 * Collects a textual thread dump of the current JVM process.
 * The format is human-readable and includes thread names, states,
 * and stack traces for all live threads at the moment of invocation.
 */
fun collectThreadDump(): String =
    buildString {
        val all = Thread.getAllStackTraces()

        // Include some header info
        append("Thread dump: ")
        append(java.time.ZonedDateTime.now())
        append('\n')

        for ((thread, stack) in all.entries.sortedBy { it.key.name }) {
            append(
                "\"${thread.name}\" " +
                    "Id=${thread.id} " +
                    (if (thread.isDaemon) "daemon " else "") +
                    "prio=${thread.priority} " +
                    "state=${thread.state}",
            )
            append('\n')
            for (traceElement in stack) {
                append("    at ")
                append(traceElement.className)
                append('.')
                append(traceElement.methodName)
                if (traceElement.isNativeMethod) {
                    append("(Native Method)")
                } else {
                    val file = traceElement.fileName ?: "Unknown Source"
                    val line = if (traceElement.lineNumber >= 0) ":${traceElement.lineNumber}" else ""
                    append('(')
                    append(file)
                    append(line)
                    append(')')
                }
                append('\n')
            }
            append('\n')
        }
    }

fun dumpThreadsToFile(file: File) {
    file.parentFile.mkdirs()
    file.writeText(collectThreadDump())
}
