package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.FieldHandler
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerByClass
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerByRuntimeClass
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerExecution
import org.jetbrains.kotlinx.jupyter.api.VariableDeclarationCallback
import org.jetbrains.kotlinx.jupyter.api.VariableUpdateCallback
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty

object FieldHandlerFactory {
    fun createHandler(kClass: KClass<*>, execution: FieldHandlerExecution<*>, typeDetection: TypeDetection): FieldHandler {
        return when (typeDetection) {
            TypeDetection.COMPILE_TIME -> FieldHandlerByClass(kClass, execution)
            TypeDetection.RUNTIME -> FieldHandlerByRuntimeClass(kClass, execution)
        }
    }

    inline fun <reified T : Any> createHandler(execution: FieldHandlerExecution<*>, typeDetection: TypeDetection): FieldHandler {
        return createHandler(T::class, execution, typeDetection)
    }

    fun <T> createDeclareExecution(callback: VariableDeclarationCallback<T>): FieldHandlerExecution<T> {
        return FieldHandlerExecution(callback)
    }

    fun <T> createUpdateExecution(callback: VariableUpdateCallback<T>): FieldHandlerExecution<T> {
        return FieldHandlerExecution { host, value, property ->
            val tempField = callback(host, value, property)
            if (tempField != null) {
                val valOrVar = if (property is KMutableProperty) "var" else "val"
                val redeclaration = "$valOrVar ${property.name} = $tempField"
                host.execute(redeclaration)
            }
        }
    }

    inline fun <reified T : Any> createDeclareHandler(typeDetection: TypeDetection, noinline callback: VariableDeclarationCallback<T>): FieldHandler {
        return createHandler<T>(createDeclareExecution(callback), typeDetection)
    }

    inline fun <reified T : Any> createUpdateHandler(typeDetection: TypeDetection, noinline callback: VariableUpdateCallback<T>): FieldHandler {
        return createHandler<T>(createUpdateExecution(callback), typeDetection)
    }
}
