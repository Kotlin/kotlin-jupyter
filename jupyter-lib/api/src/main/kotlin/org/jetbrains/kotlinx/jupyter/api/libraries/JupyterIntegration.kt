package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.GenerativeTypeHandler
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.RendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.SubtypeRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.TypeHandlerExecution

/**
 * Base class for library integration with Jupyter Kernel via DSL
 * Derive from this class and pass registration callback into constructor
 */
abstract class JupyterIntegration(private val register: Builder.(Notebook<*>?) -> Unit) : LibraryDefinitionProducer {

    class Builder {

        private val renderers = mutableListOf<RendererTypeHandler>()

        private val initCallbacks = mutableListOf<Execution>()

        private val initCellCallbacks = mutableListOf<Execution>()

        private val shutdownCallbacks = mutableListOf<Execution>()

        private val converters = mutableListOf<GenerativeTypeHandler>()

        private val annotations = mutableListOf<GenerativeTypeHandler>()

        private val imports = mutableListOf<String>()

        private val dependencies = mutableListOf<String>()

        private val repositories = mutableListOf<String>()

        fun addRenderer(handler: RendererTypeHandler) {
            renderers.add(handler)
        }

        fun addTypeConverter(handler: GenerativeTypeHandler) {
            converters.add(handler)
        }

        fun addAnnotationHandler(handler: GenerativeTypeHandler) {
            annotations.add(handler)
        }

        inline fun <reified T : Any> render(noinline renderer: (T) -> Any) {
            val execution = TypeHandlerExecution { _, value, resultFieldName ->
                KotlinKernelHost.Result(renderer(value as T), resultFieldName)
            }
            addRenderer(SubtypeRendererTypeHandler(T::class, execution))
        }

        fun import(path: String) {
            imports.add(path)
        }

        fun dependency(path: String) {
            dependencies.add(path)
        }

        fun repository(path: String) {
            repositories.add(path)
        }

        fun onLibraryLoaded(callback: () -> Unit) {
            initCallbacks.add(Execution { _ -> callback() })
        }

        fun onKernelShutdown(callback: () -> Unit) {
            shutdownCallbacks.add(Execution { _ -> callback() })
        }

        fun beforeCellExecution(callback: () -> Unit) {
            initCellCallbacks.add(Execution { _ -> callback() })
        }

        // TODO: use callback
        inline fun <reified T> generateCodeOnVariable(handler: Code) {
            val className = T::class.qualifiedName!!
            addTypeConverter(GenerativeTypeHandler(className, handler))
        }

        // TODO: use callback
        inline fun <reified T> generateCodeOnAnnotation(handler: Code) {
            val className = T::class.qualifiedName!!
            addAnnotationHandler(GenerativeTypeHandler(className, handler))
        }

        internal fun getDefinition() =
            LibraryDefinitionImpl(
                init = initCallbacks,
                renderers = renderers,
                converters = converters,
                imports = imports,
                dependencies = dependencies,
                repositories = repositories,
                initCell = initCellCallbacks,
                shutdown = shutdownCallbacks,
                annotations = annotations
            )
    }

    override fun getDefinitions(notebook: Notebook<*>?): List<LibraryDefinition> {
        val builder = Builder()
        register(builder, notebook)
        return listOf(builder.getDefinition())
    }
}
