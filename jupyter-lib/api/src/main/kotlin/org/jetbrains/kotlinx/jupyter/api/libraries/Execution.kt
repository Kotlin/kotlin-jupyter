package org.jetbrains.kotlinx.jupyter.api.libraries

/**
 * Logic to be executed with Kernel REPL
 */
fun interface Execution<T> : VariablesSubstitutionAware<Execution<T>> {
    /**
     * Execute itself with a given [host]
     *
     * @param host Kernel host to be executed on
     * @return Execution result
     */
    fun execute(host: ExecutionHost): T

    override fun replaceVariables(mapping: Map<String, String>): Execution<T> = this
}
