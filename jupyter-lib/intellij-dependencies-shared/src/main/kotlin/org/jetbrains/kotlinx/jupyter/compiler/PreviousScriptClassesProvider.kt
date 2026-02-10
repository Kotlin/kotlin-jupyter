package org.jetbrains.kotlinx.jupyter.compiler

import kotlin.script.experimental.api.KotlinType

fun interface PreviousScriptClassesProvider {
    fun get(): List<KotlinType>
}
