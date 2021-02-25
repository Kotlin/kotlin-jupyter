package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.FieldHandler
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerExecution
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.compiler.util.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.compiler.util.rethrowAsLibraryException
import org.jetbrains.kotlinx.jupyter.joinToLines
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.isAccessible

class FieldsProcessorImpl(
    private val contextUpdater: ContextUpdater,
) : FieldsProcessor {

    private val handlers = mutableListOf<FieldHandler>()

    override fun register(handler: FieldHandler) {
        handlers.add(handler)
    }

    override fun process(host: KotlinKernelHost): List<Code> {
        val conversionCodes = mutableMapOf<KProperty<*>, Code>()
        val initCodes = mutableListOf<Code>()
        val variablePlaceholder = "\$it"
        val tempFieldPrefix = "___"

        for (it in contextUpdater.context.vars.values.filter { !it.name.startsWith(tempFieldPrefix) }) {
            val property = it.descriptor
            property.isAccessible = true
            val value = it.value ?: continue
            val propertyType = property.returnType
            val notNullType = propertyType.withNullability(false)
            val handler = handlers.asIterable().firstOrNull { it.acceptsType(notNullType) }
            if (handler != null) {
                val execution = handler.execution as FieldHandlerExecution<Any>
                rethrowAsLibraryException(LibraryProblemPart.CONVERTERS) {
                    execution.execute(host, value, property)
                }
                continue
            }
            if (propertyType.isMarkedNullable) {
                conversionCodes[property] = "$variablePlaceholder!!"
            }
        }

        if (conversionCodes.isNotEmpty()) {
            val initCode = initCodes.joinToLines()
            val tempFieldsCode = conversionCodes
                .map { "val $tempFieldPrefix${it.key.name} = ${it.key.name}" }
                .joinToLines()

            val newFieldsCode = conversionCodes
                .mapValues { it.value.replace(variablePlaceholder, "$tempFieldPrefix${it.key.name}") }
                .map {
                    val valOrVar = if (it.key is KMutableProperty) "var" else "val"
                    "$valOrVar ${it.key.name} = ${it.value}"
                }
                .joinToLines()

            return listOf("$initCode\n$tempFieldsCode", newFieldsCode)
        }
        return emptyList()
    }
}
