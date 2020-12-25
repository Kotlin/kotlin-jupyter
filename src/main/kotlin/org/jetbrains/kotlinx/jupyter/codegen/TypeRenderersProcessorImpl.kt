package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.PrecompiledRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.RendererTypeHandler
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater

class TypeRenderersProcessorImpl(
    private val host: KotlinKernelHost,
    private val contextUpdater: ContextUpdater,
) : TypeRenderersProcessor {
    private var counter = 0
    private val typeRenderers: MutableList<HandlerWithInfo> = mutableListOf()

    override tailrec fun renderResult(value: Any?, fieldName: String?): Any? {
        if (value == null) return null
        val (handler, id) = typeRenderers.firstOrNull { it.handler.acceptsType(value::class) }
            ?: return value
        return if (id == null) {
            val (resultValue, resultFieldName) = handler.execution.execute(host, value, fieldName)
            renderResult(resultValue, resultFieldName)
        } else {
            val methodName = getMethodName(id)
            contextUpdater.update()
            val functionInfo = contextUpdater.context.functions[methodName]!!
            val resultValue = functionInfo.function.call(functionInfo.line, value)
            renderResult(resultValue, null)
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
