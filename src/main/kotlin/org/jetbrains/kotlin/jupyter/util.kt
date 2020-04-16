package org.jetbrains.kotlin.jupyter

import kotlinx.coroutines.*
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.slf4j.Logger
import java.io.File
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
        split("\n").map { it.split('=') }.filter { it.count() == 2 }.map { it[0] to it[1] }.toMap()

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
        val location: CompilerMessageLocation?
)

internal fun <T> ResultWithDiagnostics<T>.getErrors(): CompilationErrors =
        CompilationErrors(
                reports.joinToString("\n") { report ->
                    report.location?.let { loc ->
                        CompilerMessageLocation.create(
                                report.sourcePath,
                                loc.start.line,
                                loc.start.col,
                                loc.end?.line,
                                loc.end?.col,
                                null
                        )?.toStringExt()?.let {
                            "$it "
                        }
                    }.orEmpty() + report.message
                },
                reports.firstOrNull {
                    when (it.severity) {
                        ScriptDiagnostic.Severity.ERROR -> true
                        ScriptDiagnostic.Severity.FATAL -> true
                        else -> false
                    }
                }?.let {
                    val loc = it.location ?: return@let null
                    CompilerMessageLocation.create(
                            it.sourcePath,
                            loc.start.line,
                            loc.start.col,
                            loc.end?.line,
                            loc.end?.col,
                            null
                    )
                }
        )

/**
 * Converts its receiver to string with regard to its [CompilerMessageLocation.lineEnd] and
 * [CompilerMessageLocation.columnEnd] fields
 * @receiver CompilerMessageLocation
 * @return String
 */
fun CompilerMessageLocation.toStringExt(): String {
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
