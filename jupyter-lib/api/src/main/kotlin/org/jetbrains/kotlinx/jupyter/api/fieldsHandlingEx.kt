package org.jetbrains.kotlinx.jupyter.api

import java.lang.reflect.Field
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaField

class FieldInfo(
    val kotlinProperty: KProperty<*>?,
    val javaField: Field,
)

val FieldInfo.name: VariableName get() = kotlinProperty?.name ?: javaField.name
val FieldInfo.isCellResult: Boolean get() = kotlinProperty == null && name.startsWith("res")

private fun KProperty<*>.toFieldInfo() = FieldInfo(this, javaField ?: throw IllegalArgumentException("Property $this should have backing field"))

fun interface FieldHandlerExecutionEx<T> : FieldHandlerExecution<T> {
    fun execute(host: KotlinKernelHost, value: T, fieldInfo: FieldInfo): FieldValue?

    override fun execute(host: KotlinKernelHost, value: T, property: KProperty<*>) {
        execute(host, value, property.toFieldInfo())
    }
}

interface FieldHandlerEx : FieldHandler {
    /**
     * Tells if this handler accepts the given property
     * Called for each variable in the cells executed by users,
     * except those names are starting from [TEMP_PROPERTY_PREFIX]
     * or those that have been already consumed by another handler
     *
     * @param value Property value
     * @param fieldInfo Property runtime information
     */
    fun accepts(value: Any?, fieldInfo: FieldInfo): Boolean

    override fun accepts(value: Any?, property: KProperty<*>): Boolean {
        return accepts(value, property.toFieldInfo())
    }

    /**
     * Execution to handle conversion.
     * Should not throw if [accepts] returns true
     * Called for each property for which [accepts] returned true
     */
    override val execution: FieldHandlerExecutionEx<*>
}

class ResultFieldUpdateHandler(
    val updateCondition: (value: Any?, field: Field) -> Boolean,
    updateAction: (host: KotlinKernelHost, value: Any?, field: Field) -> VariableName?,
) : FieldHandlerEx {
    override val execution: FieldHandlerExecutionEx<Any?> = FieldHandlerExecutionEx { host, value, fieldInfo ->
        val field = fieldInfo.javaField
        val tempField = updateAction(host, value, field)
        if (tempField != null) {
            val fieldName = field.name
            host.execute("val $fieldName = $tempField; $fieldName")
        } else null
    }

    override fun accepts(value: Any?, fieldInfo: FieldInfo): Boolean {
        if (!fieldInfo.isCellResult) return false
        return updateCondition(value, fieldInfo.javaField)
    }
}
