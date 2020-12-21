package org.jetbrains.kotlinx.jupyter.api.libraries

interface VariablesSubstitutionAware<out T> {
    fun replaceVariables(mapping: Map<String, String>): T
}
