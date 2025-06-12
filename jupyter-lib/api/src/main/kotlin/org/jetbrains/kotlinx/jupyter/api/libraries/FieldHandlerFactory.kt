package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.FieldHandler
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerByClass
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerByRuntimeClass
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerExecution
import org.jetbrains.kotlinx.jupyter.api.VariableDeclarationCallback
import org.jetbrains.kotlinx.jupyter.api.VariableUpdateCallback
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KType
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf

object FieldHandlerFactory {
    fun createHandler(
        kType: KType,
        execution: FieldHandlerExecution<*>,
        typeDetection: TypeDetection,
    ): FieldHandler =
        when (typeDetection) {
            TypeDetection.COMPILE_TIME -> FieldHandlerByClass(kType, execution)
            TypeDetection.RUNTIME -> FieldHandlerByRuntimeClass(kType.classifier as KClass<*>, execution)
        }

    @Deprecated("Please use KType API", replaceWith = ReplaceWith("createHandler(kClass.starProjectedType, execution, typeDetection)"))
    fun createHandler(
        kClass: KClass<*>,
        execution: FieldHandlerExecution<*>,
        typeDetection: TypeDetection,
    ): FieldHandler = createHandler(kClass.starProjectedType, execution, typeDetection)

    inline fun <reified T : Any> createHandler(
        execution: FieldHandlerExecution<*>,
        typeDetection: TypeDetection,
    ): FieldHandler = createHandler(typeOf<T>(), execution, typeDetection)

    fun <T> createDeclareExecution(callback: VariableDeclarationCallback<T>): FieldHandlerExecution<T> = FieldHandlerExecution(callback)

    fun <T> createUpdateExecution(callback: VariableUpdateCallback<T>): FieldHandlerExecution<T> =
        FieldHandlerExecution { host, value, property ->
            val tempField = callback(host, value, property)
            if (tempField != null) {
                val valOrVar = if (property is KMutableProperty) "var" else "val"
                val redeclaration = "$valOrVar `${property.name}` = `$tempField`"
                host.execute(redeclaration)
            }
        }

    inline fun <reified T : Any> createDeclareHandler(
        typeDetection: TypeDetection,
        noinline callback: VariableDeclarationCallback<T>,
    ): FieldHandler = createHandler<T>(createDeclareExecution(callback), typeDetection)

    inline fun <reified T : Any> createUpdateHandler(
        typeDetection: TypeDetection,
        noinline callback: VariableUpdateCallback<T>,
    ): FieldHandler = createHandler<T>(createUpdateExecution(callback), typeDetection)
}
