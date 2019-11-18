package org.jetbrains.kotlin.jupyter.repl.reflect

import kotlin.reflect.KProperty

import org.jetbrains.kotlin.jupyter.repl.reflect.KotlinReflectUtil.shorten

class KotlinVariableInfo(private val value: Any?, private val descriptor: KProperty<*>) {

    val name: String
        get() = descriptor.name

    val type: String
        get() = descriptor.returnType.toString()

    fun toString(shortenTypes: Boolean): String {
        var type: String = type
        if (shortenTypes) {
            type = shorten(type)
        }
        return "$name: $type = $value"
    }

    override fun toString(): String {
        return toString(false)
    }
}
