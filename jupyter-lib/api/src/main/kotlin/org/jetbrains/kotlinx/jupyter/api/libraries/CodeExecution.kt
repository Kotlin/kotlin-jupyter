package org.jetbrains.kotlinx.jupyter.api.libraries

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.util.CodeExecutionSerializer
import org.jetbrains.kotlinx.jupyter.util.replaceVariables

/**
 * Code snippet ready to be evaluated with kernel REPL
 *
 * @property code Snippet text
 * @constructor Create code execution with the given [code]
 */
@Serializable(with = CodeExecutionSerializer::class)
class CodeExecution(val code: Code) : Execution {
    override fun execute(host: KotlinKernelHost): Any? {
        return host.execute(code)
    }

    override fun replaceVariables(mapping: Map<String, String>): CodeExecution {
        return CodeExecution(replaceVariables(code, mapping))
    }
}
