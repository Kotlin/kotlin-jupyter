package org.jetbrains.kotlinx.jupyter.protocol.startup.parameters

abstract class SimpleNamedKernelParameter<T : Any>(
    val name: String,
    private val valueParser: (String, T?) -> T,
    private val valueSerializer: (T) -> String = { it.toString() },
) : NamedKernelParameter<T>(listOf(name)) {
    override fun parseValue(
        argValue: String,
        previousValue: T?,
    ): T = valueParser(argValue, previousValue)

    override fun serializeValue(value: T): String = valueSerializer(value)
}
