package org.jetbrains.kotlinx.jupyter.util

import org.jetbrains.kotlinx.jupyter.api.libraries.VariablesSubstitutionAware

fun replaceVariables(str: String, mapping: Map<String, String>) =
    mapping.asSequence().fold(str) { s, template ->
        s.replace("\$${template.key}", template.value)
    }

@JvmName("replaceVariablesString")
fun Iterable<String>.replaceVariables(mapping: Map<String, String>) = map { replaceVariables(it, mapping) }

@JvmName("replaceVariablesExecution")
fun <T : VariablesSubstitutionAware<T>> Iterable<T>.replaceVariables(mapping: Map<String, String>) = map { it.replaceVariables(mapping) }
