package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost

fun interface Execution : VariablesSubstitutionAware<Execution> {
    fun execute(host: KotlinKernelHost): Any?

    override fun replaceVariables(mapping: Map<String, String>): Execution = this
}
