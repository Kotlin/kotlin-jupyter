package org.jetbrains.kotlin.jupyter.util

import org.jetbrains.kotlin.jupyter.api.VariablesSubstitutionAvailable

fun replaceVariables(str: String, mapping: Map<String, String>) =
    mapping.asSequence().fold(str) { s, template ->
        s.replace("\$${template.key}", template.value)
    }

@JvmName("replaceVariablesString")
fun Iterable<String>.replaceVariables(mapping: Map<String, String>) = map { replaceVariables(it, mapping) }

@JvmName("replaceVariablesExecution")
fun <T : VariablesSubstitutionAvailable<T>> Iterable<T>.replaceVariables(mapping: Map<String, String>) = map { it.replaceVariables(mapping) }
