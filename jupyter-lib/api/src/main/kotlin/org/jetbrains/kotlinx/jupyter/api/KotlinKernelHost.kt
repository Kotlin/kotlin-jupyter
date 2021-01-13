package org.jetbrains.kotlinx.jupyter.api

import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.Execution
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition

/**
 * Interface representing kernel engine, the core facility for compiling and executing code snippets
 */
interface KotlinKernelHost {
    /**
     * Try to display the given value. It is only displayed if it's an instance of [Renderable]
     * or may be converted to it
     */
    fun display(value: Any)

    /**
     * Updates display data with given [id] with the new [value]
     */
    fun updateDisplay(value: Any, id: String? = null)

    /**
     * Schedules execution of the given [execution] after the completing of execution of the current cell
     */
    fun scheduleExecution(execution: Execution<*>)

    fun scheduleExecution(execution: Code) = scheduleExecution(CodeExecution(execution))

    /**
     * Executes code immediately. Note that it may lead to breaking the kernel state in some cases
     */
    fun execute(code: Code): FieldValue

    /**
     * Adds a new library via its definition. Fully interchangeable with `%use` approach
     */
    fun addLibrary(library: LibraryDefinition)
}
