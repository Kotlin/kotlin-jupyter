package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.PrecompiledRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.RendererHandler
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.rethrowAsLibraryException
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater

class RenderersProcessorImpl(
    private val contextUpdater: ContextUpdater,
) : ResultsRenderersProcessor {
    private var counter = 0
    private val renderers: MutableList<HandlerWithInfo> = mutableListOf()

    override tailrec fun renderResult(host: ExecutionHost, field: FieldValue): Any? {
        val value = field.value
        val (handler, id) = renderers.firstOrNull { it.handler.accepts(value) }
            ?: return value
        return if (id == null) {
            val newField = rethrowAsLibraryException(LibraryProblemPart.RENDERERS) {
                handler.execution.execute(host, field)
            }
            renderResult(host, newField)
        } else {
            val methodName = getMethodName(id)
            contextUpdater.update()
            val functionInfo = contextUpdater.context.functions[methodName]!!
            val resultValue = rethrowAsLibraryException(LibraryProblemPart.RENDERERS) {
                functionInfo.function.call(functionInfo.line, value)
            }
            renderResult(host, FieldValue(resultValue, null))
        }
    }

    override fun renderValue(host: ExecutionHost, value: Any?): Any? {
        return renderResult(host, FieldValue(value, null))
    }

    override fun register(renderer: RendererHandler): Code? {
        return register(renderer, true)
    }

    override fun registerWithoutOptimizing(renderer: RendererHandler) {
        register(renderer, false)
    }

    private fun register(renderer: RendererHandler, doOptimization: Boolean): Code? {
        if (!doOptimization || renderer !is PrecompiledRendererTypeHandler || !renderer.mayBePrecompiled) {
            renderers.add(HandlerWithInfo(renderer, null))
            return null
        }

        val id = counter++
        renderers.add(HandlerWithInfo(renderer, id))
        val methodName = getMethodName(id)
        return renderer.precompile(methodName, "___value")
    }

    private fun getMethodName(id: Int) = "___renderResult$id"

    private data class HandlerWithInfo(
        val handler: RendererHandler,
        val id: Int?,
    )
}
