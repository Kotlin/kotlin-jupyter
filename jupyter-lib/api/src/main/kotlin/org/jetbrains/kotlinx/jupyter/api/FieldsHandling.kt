package org.jetbrains.kotlinx.jupyter.api

import org.jetbrains.kotlinx.jupyter.util.isSubclassOfCatching
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.withNullability

typealias VariableDeclarationCallback<T> = KotlinKernelHost.(T, KProperty<*>) -> Unit

typealias VariableName = String

typealias VariableUpdateCallback<T> = KotlinKernelHost.(T, KProperty<*>) -> VariableName?

/**
 * This prefix is used for creating temporary properties names,
 * and variables which names start from this prefix are not processed by [FieldHandler] processor
 */
const val TEMP_PROPERTY_PREFIX = "___"

fun interface FieldHandlerExecution<T> {

    fun execute(host: KotlinKernelHost, value: T, property: KProperty<*>)
}

interface FieldHandler {
    /**
     * Tells if this handler accepts the given property
     * Called for each variable in the cells executed by users,
     * except those names are starting from [TEMP_PROPERTY_PREFIX]
     * or those that have been already consumed by another handler
     *
     * @param value Property value
     * @param property Property compile-time information
     */
    fun accepts(value: Any?, property: KProperty<*>): Boolean

    /**
     * Execution to handle conversion.
     * Should not throw if [accepts] returns true
     * Called for each property for which [accepts] returned true
     */
    val execution: FieldHandlerExecution<*>

    /**
     * Called one time per cell after all the variables have been processed,
     * and only if this handler accepted at least one variable
     *
     * @param host Host for running executions
     */
    fun finalize(host: KotlinKernelHost) {}
}

data class FieldHandlerWithPriority(
    val handler: FieldHandler,
    val priority: Int,
)

interface CompileTimeFieldHandler : FieldHandler {
    /**
     * Returns true if this converter accepts [type], false otherwise
     */
    fun acceptsType(type: KType): Boolean

    override fun accepts(value: Any?, property: KProperty<*>): Boolean {
        return acceptsType(property.returnType.withNullability(false))
    }
}

class FieldHandlerByClass(
    private val kType: KType,
    override val execution: FieldHandlerExecution<*>,
) : CompileTimeFieldHandler {

    @Deprecated(
        "This constructor is misleading, use either primary constructor or `FieldHandlerByRuntimeClass`",
        ReplaceWith(
            "FieldHandlerByRuntimeClass(kClass, execution)",
            "org.jetbrains.kotlinx.jupyter.api.FieldHandlerByRuntimeClass",
        ),
        DeprecationLevel.ERROR,
    )
    constructor(kClass: KClass<out Any>, execution: FieldHandlerExecution<*>) : this(kClass.starProjectedType, execution)
    override fun acceptsType(type: KType) = type.isSubtypeOf(kType)
}

class FieldHandlerByRuntimeClass<T : Any>(
    private val kClass: KClass<T>,
    override val execution: FieldHandlerExecution<*>,
) : FieldHandler {
    override fun accepts(value: Any?, property: KProperty<*>): Boolean {
        if (value == null) return false
        val valueClass = value::class
        return valueClass.isSubclassOfCatching(kClass)
    }
}

object NullabilityEraser : FieldHandler {
    private val initCodes = mutableListOf<Code>()
    private val conversionCodes = mutableListOf<Code>()

    override val execution: FieldHandlerExecution<Any?> = FieldHandlerExecution { _, _, property ->
        val propName = property.name
        val tempVarName = TEMP_PROPERTY_PREFIX + propName
        val valOrVar = if (property is KMutableProperty) "var" else "val"
        initCodes.add("val $tempVarName = $propName")
        conversionCodes.add("$valOrVar $propName = $tempVarName!!")
    }

    override fun accepts(value: Any?, property: KProperty<*>): Boolean {
        return value != null && property.returnType.isMarkedNullable
    }

    override fun finalize(host: KotlinKernelHost) {
        try {
            host.execute(initCodes.joinToString("\n"))
            host.execute(conversionCodes.joinToString("\n"))
        } finally {
            initCodes.clear()
            conversionCodes.clear()
        }
    }

    override fun toString(): String {
        return "Nullable fields handler: generates non-nullable" +
            " overrides for nullable variables with non-null values"
    }
}

data class VariableDeclaration(
    val name: VariableName,
    val value: Any?,
    val type: KType,
    val isMutable: Boolean = false,
) {
    constructor(
        name: VariableName,
        value: Any?,
        isMutable: Boolean = false,
    ) : this(
        name,
        value,
        value?.let { it::class.starProjectedType } ?: Any::class.createType(nullable = true),
        isMutable,
    )
}

fun KotlinKernelHost.declare(vararg variables: VariableDeclaration) = declare(variables.toList())
fun KotlinKernelHost.declare(vararg variables: Pair<VariableName, Any?>) = declare(variables.map { VariableDeclaration(it.first, it.second) })
