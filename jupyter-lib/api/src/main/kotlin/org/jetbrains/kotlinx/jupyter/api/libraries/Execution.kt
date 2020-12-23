package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost

/**
 * Logic to be executed with Kernel REPL
 */
fun interface Execution : VariablesSubstitutionAware<Execution> {
    /**
     * Execute itself with a given [host]
     *
     * @param host Kernel host to be executed on
     * @return Execution result
     */
    fun execute(host: KotlinKernelHost): Any?

    override fun replaceVariables(mapping: Map<String, String>): Execution = this
}
