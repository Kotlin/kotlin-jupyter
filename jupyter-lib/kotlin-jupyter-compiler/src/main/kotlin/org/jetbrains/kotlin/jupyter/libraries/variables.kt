package org.jetbrains.kotlin.jupyter.libraries

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.jetbrains.kotlin.jupyter.util.ListToMapSerializer

@Serializable
data class Variable(val name: String, val value: String, val required: Boolean = false)
object VariablesSerializer : ListToMapSerializer<Variable, String, String>(
    serializer(),
    ::Variable,
    { it.name to it.value }
)
