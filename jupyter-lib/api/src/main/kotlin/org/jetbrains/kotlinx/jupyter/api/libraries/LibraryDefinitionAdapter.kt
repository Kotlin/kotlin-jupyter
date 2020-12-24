package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.AlwaysRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.RendererTypeHandler

/**
 * Easy-to-use [LibraryDefinition] adapter. It will suit you if you're not going
 * to use plain code callbacks.
 *
 * @property repositories A list of maven repositories URLs and directories to search for dependencies
 * @property dependencies A list of maven coordinates or file paths of artifact dependencies
 * @property imports A list of default imports. Star imports are supported
 * @constructor Creates an adapter
 */
open class LibraryDefinitionAdapter(
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
