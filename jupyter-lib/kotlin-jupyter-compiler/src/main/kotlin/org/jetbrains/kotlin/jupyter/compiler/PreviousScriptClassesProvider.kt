package org.jetbrains.kotlin.jupyter.compiler

import kotlin.reflect.KClass

fun interface PreviousScriptClassesProvider {
    fun get(): List<KClass<*>>
}
