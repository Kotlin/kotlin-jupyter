package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.PrecompiledRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.ProcessingPriority
import org.jetbrains.kotlinx.jupyter.api.RendererFieldHandler
import org.jetbrains.kotlinx.jupyter.api.RendererHandlerWithPriority
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.rethrowAsLibraryException
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater
import org.jetbrains.kotlinx.jupyter.util.PriorityList

class RenderersProcessorImpl(
    private val contextUpdater: ContextUpdater,
) : ResultsRenderersProcessor {
    private var counter = 0
    private val renderers = PriorityList<HandlerWithInfo>()

    override tailrec fun renderResult(host: ExecutionHost, field: FieldValue): Any? {
        val value = field.value
        val (handler, id) = renderers.firstOrNull { it.handler.acceptsField(field) }
            ?: return value
        return if (id == null) {
            val newField = rethrowAsLibraryException(LibraryProblemPart.RENDERERS) {
                handler.execution.execute(host, field)
            }
            renderResult(host, newField)
        } else {
            val methodName = getMethodName(id)
            contextUpdater.update()
            val functionInfo = contextUpdater.context.allFunctions[methodName]!!
            val resultValue = rethrowAsLibraryException(LibraryProblemPart.RENDERERS) {
                functionInfo.function.call(functionInfo.line, value)
            }
            renderResult(host, FieldValue(resultValue, null))
        }
    }

    override fun renderValue(host: ExecutionHost, value: Any?): Any? {
        return renderResult(host, FieldValue(value, null))
    }

    override fun register(renderer: RendererFieldHandler): Code? {
        return register(renderer, ProcessingPriority.DEFAULT)
    }

    override fun register(renderer: RendererFieldHandler, priority: Int): Code? {
        return register(renderer, true, priority)
    }

    override fun registerWithoutOptimizing(renderer: RendererFieldHandler) {
        registerWithoutOptimizing(renderer, ProcessingPriority.DEFAULT)
    }

    override fun registerWithoutOptimizing(renderer: RendererFieldHandler, priority: Int) {
        register(renderer, false, priority)
    }

    private fun register(renderer: RendererFieldHandler, doOptimization: Boolean, priority: Int): Code? {
        if (!doOptimization || renderer !is PrecompiledRendererTypeHandler || !renderer.mayBePrecompiled) {
            renderers.add(HandlerWithInfo(renderer, null), priority)
            return null
        }

        val id = counter++
        renderers.add(HandlerWithInfo(renderer, id), priority)
        val methodName = getMethodName(id)
        return renderer.precompile(methodName, "___value")
    }

    override fun registeredRenderers(): List<RendererHandlerWithPriority> {
        return renderers.elementsWithPriority().map {
            RendererHandlerWithPriority(it.first.handler, it.second)
        }
    }

    override fun unregister(renderer: RendererFieldHandler) {
        val rendererInfosToRemove = renderers.elements().filter {
            it.handler === renderer
        }
        for (rendererInfo in rendererInfosToRemove) {
            renderers.remove(rendererInfo)
        }
    }

    override fun unregisterAll() {
        renderers.clear()
    }

    private fun getMethodName(id: Int) = "___renderResult$id"

    private data class HandlerWithInfo(
        val handler: RendererFieldHandler,
        val id: Int?,
    )
}
