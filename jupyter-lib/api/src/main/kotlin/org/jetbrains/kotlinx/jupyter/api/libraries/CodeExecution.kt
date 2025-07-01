package org.jetbrains.kotlinx.jupyter.api.libraries

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.util.CodeExecutionSerializer
import org.jetbrains.kotlinx.jupyter.util.replaceVariables

/**
 * Code snippet ready to be evaluated with kernel REPL
 *
 * @property code Snippet text
 * @constructor Create code execution with the given [code]
 */
@Serializable(with = CodeExecutionSerializer::class)
class CodeExecution(
    val code: Code,
) {
    fun toExecutionCallback(mapping: Map<String, String> = emptyMap()): ExecutionCallback<Any?> =
        CodeExecutionCallback(replaceVariables(code, mapping))
}
