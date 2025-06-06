package org.jetbrains.kotlinx.jupyter.compiler

import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.compilerOptions

/**
 * This interface defines the command line arguments that are being applied to the Kotlin Compiler
 * as part of the [ScriptCompilationConfiguration]. The syntax is the same as CLI Compiler Arguments,
 * e.g., `-no-stdlib`. The validity of arguments is not checked until the compiler is invoked and
 * any errors will be reported back through [ResultDiagnostics].
 *
 * Compiler arguments can be modified between each notebook cell evaluated, but this depends on
 * the [ScriptCompilationConfiguration] being updated. However, this is the default behavior.
 *
 * @see compilerOptions
 * @see org.jetbrains.kotlinx.jupyter.config.getCompilationConfiguration
 */
interface CompilerArgsConfigurator {
    /**
     * Get all compiler arguments for the current [ScriptCompilationConfiguration].
     * [configure] or [addArg] should be called before this method.
     */
    fun getArgs(): List<String>

    /**
     * This method is called as part of the [org.jetbrains.kotlinx.jupyter.codegen.FileAnnotationsProcessor] and
     * is responsible for extracting any relevant compiler args from the list of [jupyter.kotlin.CompilerArgs]
     * annotations found in the notebook cell.
     *
     * Any errors should be reported through [ResultWithDiagnostics].
     */
    fun configure(
        // The compilation configuration currently being modified
        configuration: ScriptCompilationConfiguration,
        // List of @CompilerArgs annotations found in the cell
        annotations: List<Annotation>,
    ): ResultWithDiagnostics<ScriptCompilationConfiguration>

    /**
     * Manually add a single command line argument.
     */
    fun addArg(arg: String)
}
