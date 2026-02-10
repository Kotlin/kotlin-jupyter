package org.jetbrains.kotlinx.jupyter.compiler.daemon

import kotlinx.rpc.internal.utils.InternalRpcApi
import kotlinx.rpc.krpc.internal.SerializedException
import kotlinx.rpc.krpc.internal.deserialize
import kotlinx.rpc.krpc.internal.serializeException
import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResult
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCodeCompletionVariant

@Serializable
data class SourcePositionRpc(
    val line: Int,
    val col: Int,
    val absolutePos: Int?,
)

fun SourceCode.Position.toRpc(): SourcePositionRpc = SourcePositionRpc(line = line, col = col, absolutePos = absolutePos)

fun SourcePositionRpc.fromRpc(): SourceCode.Position = SourceCode.Position(line = line, col = col, absolutePos = absolutePos)

@Serializable
data class SourceLocationRpc(
    val start: SourcePositionRpc,
    val end: SourcePositionRpc?,
)

fun SourceCode.Location.toRpc(): SourceLocationRpc = SourceLocationRpc(start = start.toRpc(), end = end?.toRpc())

fun SourceLocationRpc.fromRpc(): SourceCode.Location = SourceCode.Location(start = start.fromRpc(), end = end?.fromRpc())

@Serializable
data class ScriptDiagnosticRpc
    @InternalRpcApi
    constructor(
        val code: Int,
        val message: String,
        val severity: ScriptDiagnostic.Severity,
        val sourcePath: String?,
        val location: SourceLocationRpc?,
        val exception: SerializedException?,
    )

@OptIn(InternalRpcApi::class)
fun ScriptDiagnostic.toRpc(): ScriptDiagnosticRpc =
    ScriptDiagnosticRpc(
        code = code,
        message = message,
        severity = severity,
        sourcePath = sourcePath,
        location = location?.toRpc(),
        exception = exception?.let { serializeException(it) },
    )

@OptIn(InternalRpcApi::class)
fun ScriptDiagnosticRpc.fromRpc(): ScriptDiagnostic =
    ScriptDiagnostic(
        code = code,
        message = message,
        severity = severity,
        sourcePath = sourcePath,
        location = location?.fromRpc(),
        exception = exception?.deserialize(),
    )

@Serializable
data class SourceCodeCompletionVariantRpc(
    val text: String,
    val displayText: String,
    val tail: String,
    val icon: String,
    val deprecationLevel: DeprecationLevel?,
)

fun SourceCodeCompletionVariant.toRpc(): SourceCodeCompletionVariantRpc =
    SourceCodeCompletionVariantRpc(
        text = text,
        displayText = displayText,
        tail = tail,
        icon = icon,
        deprecationLevel = deprecationLevel,
    )

fun SourceCodeCompletionVariantRpc.fromRpc(): SourceCodeCompletionVariant =
    SourceCodeCompletionVariant(
        text = text,
        displayText = displayText,
        tail = tail,
        icon = icon,
        deprecationLevel = deprecationLevel,
    )

@Serializable
sealed class CompileResultRpc {
    @Serializable
    class Success(
        val value: CompileResult.Success,
    ) : CompileResultRpc()

    @Serializable
    data class Failure(
        val diagnostics: List<ScriptDiagnosticRpc>,
    ) : CompileResultRpc()
}

fun CompileResult.toRpc(): CompileResultRpc =
    when (this) {
        is CompileResult.Success -> CompileResultRpc.Success(this)
        is CompileResult.Failure -> CompileResultRpc.Failure(diagnostics.map { it.toRpc() })
    }

fun CompileResultRpc.fromRpc(): CompileResult =
    when (this) {
        is CompileResultRpc.Success -> value
        is CompileResultRpc.Failure -> CompileResult.Failure(diagnostics.map { it.fromRpc() })
    }
