package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.FieldHandler
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerExecution
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerWithPriority
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.TEMP_PROPERTY_PREFIX
import org.jetbrains.kotlinx.jupyter.exceptions.CompositeReplException
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater
import org.jetbrains.kotlinx.jupyter.util.PriorityList
import kotlin.reflect.jvm.isAccessible

class FieldsProcessorImpl(
    private val contextUpdater: ContextUpdater,
) : FieldsProcessorInternal {

    private val handlers = PriorityList<FieldHandler>()

    override fun register(handler: FieldHandler, priority: Int) {
        handlers.add(handler, priority)
    }

    override fun registeredHandlers(): List<FieldHandlerWithPriority> {
        return handlers.elementsWithPriority().map { FieldHandlerWithPriority(it.first, it.second) }
    }

    override fun unregister(handler: FieldHandler) {
        handlers.remove(handler)
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
            val handler = handlers.firstOrNull { it.accepts(value, property) }
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

        if (exceptions.isNotEmpty()) {
            throw CompositeReplException(exceptions, LibraryProblemPart.CONVERTERS)
        }
    }
}
