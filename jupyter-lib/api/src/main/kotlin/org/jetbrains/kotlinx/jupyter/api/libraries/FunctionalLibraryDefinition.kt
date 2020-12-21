package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.AlwaysRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.RendererTypeHandler

open class FunctionalLibraryDefinition(
    override val repositories: List<String> = emptyList(),
    override val dependencies: List<String> = emptyList(),
    override val imports: List<String> = emptyList(),
) : LibraryDefinition {
    open fun onInit(host: KotlinKernelHost): Any? {
        return Unit
    }
    open fun onCellInit(host: KotlinKernelHost): Any? {
        return Unit
    }
    open fun onShutdown(host: KotlinKernelHost): Any? {
        return Unit
    }
    open fun render(host: KotlinKernelHost, value: Any?, resultFieldName: String?): Any? {
        return value
    }

    override val init: List<Execution> by lazy {
        listOf(Execution(::onInit))
    }

    override val initCell: List<Execution> by lazy {
        listOf(Execution(::onCellInit))
    }

    override val shutdown: List<Execution> by lazy {
        listOf(Execution(::onShutdown))
    }

    override val renderers: List<RendererTypeHandler> by lazy {
        listOf(
            AlwaysRendererTypeHandler { host, value, fieldName ->
                KotlinKernelHost.Result(render(host, value, fieldName), null)
            }
        )
    }
}
