package org.jetbrains.kotlinx.jupyter.dependencies

import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode

/**
 * Creates a failure result from a single diagnostic [message].
 */
fun makeResolveFailureResult(
    message: String,
    location: SourceCode.LocationWithId? = null,
) = makeResolveFailureResult(listOf(message), location)

/**
 * Creates a failure result from multiple diagnostic [messages].
 */
fun makeResolveFailureResult(
    messages: Iterable<String>,
    location: SourceCode.LocationWithId? = null,
) = makeResolveFailureResult(messages, location, null)

/**
 * Creates a failure result from multiple diagnostic [messages] and optional [throwable].
 */
fun makeResolveFailureResult(
    messages: Iterable<String>,
    location: SourceCode.LocationWithId?,
    throwable: Throwable?,
) = ResultWithDiagnostics.Failure(
    messages.map {
        ScriptDiagnostic(ScriptDiagnostic.unspecifiedError, it, ScriptDiagnostic.Severity.WARNING, location, throwable)
    },
)
