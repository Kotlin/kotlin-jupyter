package org.jetbrains.kotlinx.jupyter.api

import kotlinx.serialization.Serializable

@Serializable
data class DeclarationInfo(
    val name: String?,
    val kind: DeclarationKind,
)

@Serializable
enum class DeclarationKind {
    UNKNOWN,
    CLASS,
    OBJECT,
    FUNCTION,
    PROPERTY,
    SCRIPT_INITIALIZER,
}
