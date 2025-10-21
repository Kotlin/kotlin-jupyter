package org.jetbrains.kotlinx.jupyter.dependencies.test

import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.script.experimental.api.ResultWithDiagnostics

/**
 * Kotest-style helper that asserts this result is Success and returns it.
 */
fun <T> ResultWithDiagnostics<T>.shouldBeSuccess(): ResultWithDiagnostics.Success<T> = shouldBeTypeOf<ResultWithDiagnostics.Success<T>>()
