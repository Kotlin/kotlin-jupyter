package org.jetbrains.kotlinx.jupyter

import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic

// Work-around for KT-74685
// We go through all reports and combine reports with the same text and location
fun ResultWithDiagnostics.Failure.removeDuplicates(): ResultWithDiagnostics.Failure {
    val noDuplicateList = LinkedHashSet<ScriptDiagnostic>(reports)
    return ResultWithDiagnostics.Failure(noDuplicateList.toList())
}
