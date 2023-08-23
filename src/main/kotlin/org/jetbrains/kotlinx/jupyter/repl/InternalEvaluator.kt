package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScriptsData
import org.jetbrains.kotlinx.jupyter.config.JupyterCompilingOptions
import org.jetbrains.kotlinx.jupyter.repl.workflow.EvaluatorWorkflowListener
import kotlin.reflect.KClass

interface InternalEvaluator {

    var logExecution: Boolean
    var writeCompiledClasses: Boolean
    var serializeScriptData: Boolean

    val lastKClass: KClass<*>
    val lastClassLoader: ClassLoader

    val variablesHolder: Map<String, VariableState>

    val cellVariables: Map<Int, Set<String>>

    /**
     * Executes code snippet
     * @throws IllegalStateException if this method was invoked recursively
     */
    fun eval(code: Code, compilingOptions: JupyterCompilingOptions = JupyterCompilingOptions.DEFAULT, evaluatorWorkflowListener: EvaluatorWorkflowListener? = null): InternalEvalResult

    /**
     * Pop a serialized form of recently added compiled scripts
     *
     * This operation is stateful: second call of this method in a row always
     * returns empty data or null
     */
    fun popAddedCompiledScripts(): SerializedCompiledScriptsData = SerializedCompiledScriptsData.EMPTY
}
