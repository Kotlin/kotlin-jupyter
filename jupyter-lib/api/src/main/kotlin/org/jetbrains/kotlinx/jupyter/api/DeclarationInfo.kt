package org.jetbrains.kotlinx.jupyter.api

interface DeclarationInfo {
    val name: String?
    val kind: DeclarationKind
}

enum class DeclarationKind {
    UNKNOWN, CLASS, OBJECT, FUNCTION, PROPERTY, SCRIPT_INITIALIZER
}
