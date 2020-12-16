package org.jetbrains.kotlin.jupyter.compiler

import kotlin.script.experimental.api.KotlinType

fun interface PreviousScriptClassesProvider {
    fun get(): List<KotlinType>
}
