package org.jetbrains.kotlinx.jupyter.common

interface ReplEnum<T : Enum<*>> {
    val codeInsightValues: List<CodeInsightValue<T>>

    fun valueOfOrNull(name: String): CodeInsightValue<T>?

    class CodeInsightValue<V : Enum<*>>(
        val value: V,
        val name: String,
        val description: String,
        val type: Type,
    )

    interface Type {
        val name: String
    }
}
