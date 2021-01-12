package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.Code
import kotlin.reflect.KClass

interface InternalEvaluator {

    var logExecution: Boolean
    var writeCompiledClasses: Boolean

    val lastKClass: KClass<*>
    val lastClassLoader: ClassLoader

    /**
     * Executes code snippet
     * @throws IllegalStateException if this method was invoked recursively
     */
    fun eval(code: Code, onInternalIdGenerated: ((Int) -> Unit)? = null): InternalEvalResult
}
