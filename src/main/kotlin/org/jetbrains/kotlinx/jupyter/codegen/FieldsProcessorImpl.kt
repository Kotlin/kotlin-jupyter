package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.FieldHandler
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerExecution
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerWithPriority
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.TEMP_PROPERTY_PREFIX
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.throwLibraryException
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater
import org.jetbrains.kotlinx.jupyter.repl.impl.AbstractExtensionsProcessor
import kotlin.reflect.jvm.isAccessible

class FieldsProcessorImpl(
    private val contextUpdater: ContextUpdater,
) : AbstractExtensionsProcessor<FieldHandler>(latterFirst = true), FieldsProcessorInternal {

    override fun registeredHandlers(): List<FieldHandlerWithPriority> {
        return extensions.elementsWithPriority().map { FieldHandlerWithPriority(it.first, it.second) }
    }

    override fun process(host: KotlinKernelHost) {
        val fieldHandlers = mutableSetOf<FieldHandler>()
        val exceptions = mutableListOf<Throwable>()
        val variableInfos = contextUpdater.context.currentVariables.values.filter {
            !it.name.startsWith(TEMP_PROPERTY_PREFIX)
        }

        for (info in variableInfos) {
            val property = info.descriptor
            property.isAccessible = true
            val value = info.value ?: continue
            val handler = extensions.firstOrNull { it.accepts(value, property) }
            if (handler != null) {
                @Suppress("UNCHECKED_CAST")
                val execution = handler.execution as FieldHandlerExecution<Any>
                try {
                    execution.execute(host, value, property)
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
    }
}
