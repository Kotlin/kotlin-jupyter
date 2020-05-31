package org.jetbrains.kotlin.jupyter

import kotlinx.coroutines.*
import org.slf4j.Logger
import java.io.File
import java.io.Serializable
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.KJvmEvaluatedSnippet
import kotlin.script.experimental.util.LinkedSnippet
import kotlin.script.experimental.util.toList

fun <T> catchAll(body: () -> T): T? = try {
    body()
} catch (e: Exception) {
    null
}

fun <T> Logger.catchAll(msg: String = "", body: () -> T): T? = try {
    body()
} catch (e: Exception) {
    this.error(msg, e)
    null
}

fun <T> T.validOrNull(predicate: (T) -> Boolean): T? = if (predicate(this)) this else null

fun <T> T.asDeferred(): Deferred<T> = this.let { GlobalScope.async { it } }

fun File.existsOrNull() = if (exists()) this else null

fun <T, R> Deferred<T>.asyncLet(selector: suspend (T) -> R): Deferred<R> = this.let {
    GlobalScope.async {
        selector(it.await())
    }
}

fun <T> Deferred<T>.awaitBlocking(): T = if (isCompleted) getCompleted() else runBlocking { await() }

fun String.parseIniConfig() =
        lineSequence().map { it.split('=') }.filter { it.count() == 2 }.map { it[0] to it[1] }.toMap()

fun List<String>.joinToLines() = joinToString("\n")

fun File.tryReadIniConfig() =
        existsOrNull()?.let {
            catchAll { it.readText().parseIniConfig() }
        }



fun LinkedSnippet<KJvmEvaluatedSnippet>?.instances() = this.toList { it.result.scriptInstance }.filterNotNull()

fun generateDiagnostic(fromLine: Int, fromCol: Int, toLine: Int, toCol: Int, message: String, severity: String) =
        ScriptDiagnostic(
                ScriptDiagnostic.unspecifiedError,
                message,
                ScriptDiagnostic.Severity.valueOf(severity),
                null,
                SourceCode.Location(SourceCode.Position(fromLine, fromCol), SourceCode.Position(toLine, toCol))
        )

fun withPath(path: String?, diagnostics: List<ScriptDiagnostic>): List<ScriptDiagnostic> =
        diagnostics.map { it.copy(sourcePath = path) }

internal data class CompilationErrors(
        val message: String,
        val location: CompilerMessageLocationWithEnd?
)

internal fun <T> ResultWithDiagnostics<T>.getErrors(): CompilationErrors {
    val filteredReports = reports.filter {
        it.code != ScriptDiagnostic.incompleteCode
    }

    return CompilationErrors(
            filteredReports.joinToString("\n") { report ->
                report.location?.let { loc ->
                    CompilerMessageLocationWithEnd.create(
                            report.sourcePath,
                            loc.start.line,
                            loc.start.col,
                            loc.end?.line,
                            loc.end?.col,
                            null
                    )?.toString()?.let {
                        "$it "
                    }
                }.orEmpty() + report.message
            },
            filteredReports.firstOrNull {
                when (it.severity) {
                    ScriptDiagnostic.Severity.ERROR -> true
                    ScriptDiagnostic.Severity.FATAL -> true
                    else -> false
                }
            }?.let {
                val loc = it.location ?: return@let null
                CompilerMessageLocationWithEnd.create(
                        it.sourcePath,
                        loc.start.line,
                        loc.start.col,
                        loc.end?.line,
                        loc.end?.col,
                        null
                )
            }
    )
}


data class CompilerMessageLocationWithEnd (
        val path: String,
        val line: Int,
        val column: Int,
        val lineEnd: Int?,
        val columnEnd: Int?,
        val lineContent: String?,
) : Serializable {
    override fun toString(): String {
        val start =
                if (line == -1 && column == -1) ""
                else "$line:$column"
        val end =
                if (lineEnd == -1 && columnEnd == -1) ""
                else if (lineEnd == line) " - $columnEnd"
                else " - $lineEnd:$columnEnd"
        val loc = if (start.isEmpty() && end.isEmpty()) "" else " ($start$end)"
        return path + loc
    }

    companion object {
        fun create (path: String?,
                    line: Int,
                    column: Int,
                    lineEnd: Int?,
                    columnEnd: Int?,
                    lineContent: String?) =
            if (path == null) null else CompilerMessageLocationWithEnd(path, line, column, lineEnd, columnEnd, lineContent)

    }
}
