package org.jetbrains.kotlinx.jupyter.api

import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType

typealias VariableDeclarationCallback<T> = KotlinKernelHost.(T, KProperty<*>) -> Unit

typealias VariableName = String

typealias VariableUpdateCallback<T> = KotlinKernelHost.(T, KProperty<*>) -> VariableName?

fun interface FieldHandlerExecution<T> {

    fun execute(host: KotlinKernelHost, value: T, property: KProperty<*>)
}

interface FieldHandler {
    /**
     * Returns true if this converter accepts [type], false otherwise
     */
    fun acceptsType(type: KType): Boolean

    /**
     * Execution to handle conversion.
     * Should not throw if [acceptsType] returns true
     */
    val execution: FieldHandlerExecution<*>
}

class FieldHandlerByClass(
    private val kClass: KClass<out Any>,
    override val execution: FieldHandlerExecution<*>,
) : FieldHandler {
    override fun acceptsType(type: KType) = type.isSubtypeOf(kClass.starProjectedType)
}

data class PropertyDeclaration(
    val name: VariableName,
    val value: Any?,
    val type: KType,
    val isMutable: Boolean = false,
) {
    constructor(
        name: VariableName,
        value: Any?,
        isMutable: Boolean = false
    ) : this(
        name,
        value,
        value?.let { it::class.starProjectedType } ?: Any::class.createType(nullable = true),
        isMutable
    )
}

fun KotlinKernelHost.declareProperties(vararg properties: PropertyDeclaration) = declareProperties(properties.toList())
fun KotlinKernelHost.declareProperties(vararg properties: Pair<VariableName, Any?>) = declareProperties(properties.map { PropertyDeclaration(it.first, it.second) })
