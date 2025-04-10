package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.config.JupyterCompilingOptions
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.repl.execution.EvaluatorWorkflowListener
import org.jetbrains.kotlinx.jupyter.repl.result.InternalEvalResult
import org.jetbrains.kotlinx.jupyter.repl.result.SerializedCompiledScriptsData
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ReplCompiler
import kotlin.script.experimental.api.ReplEvaluator

/**
 * The [InternalEvaluator] is the Kotlin Kernel wrapper around [ReplCompiler] and [ReplEvaluator].
 * It is responsible for both compiling and evaluating user snippets.
 *
 * This interface is only used internally in the kernel and should not be exposed to end users.
 */
interface InternalEvaluator {
    var executionLogging: ExecutedCodeLogging
    var writeCompiledClasses: Boolean
    var serializeScriptData: Boolean

    val lastKClass: KClass<*>
    val lastClassLoader: ClassLoader

    val variablesHolder: Map<String, VariableState>

    val cellVariables: Map<Int, Set<String>>

    /**
     * Executes code snippet
     * @throws IllegalStateException if this method was invoked recursively
     * @throws ReplCompilerException if the snippet could not be compiled or evaluated.
     * @return If the snippet was compiled and evaluated successfully, the output is wrapped
     * and returned in a [InternalEvalResult].
     */
    fun eval(
        code: Code,
        compilingOptions: JupyterCompilingOptions = JupyterCompilingOptions.DEFAULT,
        evaluatorWorkflowListener: EvaluatorWorkflowListener? = null,
    ): InternalEvalResult

    /**
     * Pop a serialized form of recently added compiled scripts
     *
     * This operation is stateful: second call of this method in a row always
     * returns empty data or null
     */
    fun popAddedCompiledScripts(): SerializedCompiledScriptsData = SerializedCompiledScriptsData.EMPTY
}
