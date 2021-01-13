package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.PrecompiledRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.RendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater

class TypeRenderersProcessorImpl(
    private val contextUpdater: ContextUpdater,
) : TypeRenderersProcessor {
    private var counter = 0
    private val typeRenderers: MutableList<HandlerWithInfo> = mutableListOf()

    override tailrec fun renderResult(host: ExecutionHost, field: FieldValue): Any? {
        val value = field.value ?: return null
        val (handler, id) = typeRenderers.firstOrNull { it.handler.acceptsType(value::class) }
            ?: return value
        return if (id == null) {
            val newField = handler.execution.execute(host, field)
            renderResult(host, newField)
        } else {
            val methodName = getMethodName(id)
            contextUpdater.update()
            val functionInfo = contextUpdater.context.functions[methodName]!!
            val resultValue = functionInfo.function.call(functionInfo.line, value)
            renderResult(host, FieldValue(resultValue, null))
        }
    }

    override fun register(renderer: RendererTypeHandler): Code? {
        if (renderer !is PrecompiledRendererTypeHandler || !renderer.mayBePrecompiled) {
            typeRenderers.add(HandlerWithInfo(renderer, null))
            return null
        }

        val id = counter++
        typeRenderers.add(HandlerWithInfo(renderer, id))
        val methodName = getMethodName(id)
        return renderer.precompile(methodName, "___value")
    }

    private fun getMethodName(id: Int) = "___renderResult$id"

    private data class HandlerWithInfo(
        val handler: RendererTypeHandler,
        val id: Int?,
    )
}
