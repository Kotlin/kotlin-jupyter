package org.jetbrains.kotlinx.jupyter.util

import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.VariablesSubstitutionAware

/**
 * Replace all $<name> substrings in [str] with corresponding
 * [mapping] values
 */
fun replaceVariables(
    str: String,
    mapping: Map<String, String>,
) = mapping.asSequence().fold(str) { s, template ->
    s.replace("\$${template.key}", template.value)
}

@JvmName("replaceVariablesString")
fun Iterable<String>.replaceVariables(mapping: Map<String, String>) = map { replaceVariables(it, mapping) }

@JvmName("replaceVariables")
fun <T : VariablesSubstitutionAware<T>> Iterable<T>.replaceVariables(mapping: Map<String, String>) = map { it.replaceVariables(mapping) }

@JvmName("replaceVariablesExecution")
fun Iterable<CodeExecution>.replaceVariables(mapping: Map<String, String>) = map { it.toExecutionCallback(mapping) }
