package org.jetbrains.kotlinx.jupyter.codegen

import jupyter.kotlin.name
import org.jetbrains.kotlinx.jupyter.api.FieldHandler
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerEx
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerExecution
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerExecutionEx
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerWithPriority
import org.jetbrains.kotlinx.jupyter.api.FieldInfo
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.TEMP_PROPERTY_PREFIX
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.throwLibraryException
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater
import org.jetbrains.kotlinx.jupyter.repl.execution.AbstractExtensionsProcessor
import kotlin.reflect.jvm.isAccessible

class FieldsProcessorImpl(
    private val contextUpdater: ContextUpdater,
) : AbstractExtensionsProcessor<FieldHandler>(latterFirst = true), FieldsProcessorInternal {

    override fun registeredHandlers(): List<FieldHandlerWithPriority> {
        return extensions.elementsWithPriority().map { FieldHandlerWithPriority(it.first, it.second) }
    }

    override fun process(host: KotlinKernelHost): FieldValue? {
        val fieldHandlers = mutableSetOf<FieldHandler>()
        val exceptions = mutableListOf<Throwable>()
        var newResultField: FieldValue? = null
        val variableInfos = contextUpdater.context.currentVariables.values.filter {
            !it.name.startsWith(TEMP_PROPERTY_PREFIX)
        }

        for (info in variableInfos) {
            val value = info.value ?: continue

            info.kotlinProperty?.isAccessible = true
            info.javaField.isAccessible = true
            val fieldInfo = FieldInfo(info.kotlinProperty, info.javaField)

            val handler = extensions.firstOrNull { it.acceptsEx(value, fieldInfo) }
            if (handler != null) {
                @Suppress("UNCHECKED_CAST")
                val execution = handler.execution as FieldHandlerExecution<Any>
                try {
                    execution.executeEx(host, value, fieldInfo)?.let { result ->
                        if (newResultField == null) {
                            newResultField = result
                        }
                    }

                    fieldHandlers.add(handler)
                } catch (e: Throwable) {
                    exceptions.add(e)
                }
            }
        }

        for (handler in fieldHandlers) {
            try {
                handler.finalize(host)
            } catch (e: Throwable) {
                exceptions.add(e)
            }
        }

        exceptions.throwLibraryException(LibraryProblemPart.CONVERTERS)

        return newResultField
    }
}

private fun FieldHandler.acceptsEx(value: Any?, fieldInfo: FieldInfo): Boolean {
    return if (this is FieldHandlerEx) {
        this.accepts(value, fieldInfo)
    } else {
        val property = fieldInfo.kotlinProperty ?: return false
        accepts(value, property)
    }
}

private fun <T> FieldHandlerExecution<T>.executeEx(host: KotlinKernelHost, value: T, fieldInfo: FieldInfo): FieldValue? {
    return if (this is FieldHandlerExecutionEx<T>) {
        this.execute(host, value, fieldInfo)
    } else {
        val property = fieldInfo.kotlinProperty ?: return null
        execute(host, value, property)
        null
    }
}
