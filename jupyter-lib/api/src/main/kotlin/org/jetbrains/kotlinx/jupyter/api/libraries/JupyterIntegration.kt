package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.AnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.ClassDeclarationsCallback
import org.jetbrains.kotlinx.jupyter.api.FieldHandler
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerByClass
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerExecution
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.RendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.ResultHandlerExecution
import org.jetbrains.kotlinx.jupyter.api.SubtypeRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.VariableDeclarationCallback
import org.jetbrains.kotlinx.jupyter.api.VariableUpdateCallback
import kotlin.reflect.KMutableProperty

/**
 * Base class for library integration with Jupyter Kernel via DSL
 * Derive from this class and pass registration callback into constructor
 */
abstract class JupyterIntegration(private val register: Builder.(Notebook<*>?) -> Unit) : LibraryDefinitionProducer {

    class Builder {

        private val renderers = mutableListOf<RendererTypeHandler>()

        private val initCallbacks = mutableListOf<Execution<*>>()

        private val initCellCallbacks = mutableListOf<Execution<*>>()

        private val shutdownCallbacks = mutableListOf<Execution<*>>()

        private val converters = mutableListOf<FieldHandler>()

        private val annotations = mutableListOf<AnnotationHandler>()

        private val resources = mutableListOf<LibraryResource>()

        private val imports = mutableListOf<String>()

        private val dependencies = mutableListOf<String>()

        private val repositories = mutableListOf<String>()

        fun addRenderer(handler: RendererTypeHandler) {
            renderers.add(handler)
        }

        fun addTypeConverter(handler: FieldHandler) {
            converters.add(handler)
        }

        fun addAnnotationHandler(handler: AnnotationHandler) {
            annotations.add(handler)
        }

        inline fun <reified T : Any> render(noinline renderer: (T) -> Any) {
            val execution = ResultHandlerExecution { _, property ->
                FieldValue(renderer(property.value as T), property.name)
            }
            addRenderer(SubtypeRendererTypeHandler(T::class, execution))
        }

        fun resource(resource: LibraryResource) {
            resources.add(resource)
        }

        fun import(path: String) {
            imports.add(path)
        }

        inline fun <reified T> import() {
            import(T::class.qualifiedName!!)
        }

        inline fun <reified T> importPackage() {
            val name = T::class.qualifiedName!!
            val lastDot = name.lastIndexOf(".")
            if (lastDot != -1) {
                import(name.substring(0, lastDot + 1) + "*")
            }
        }

        fun dependency(path: String) {
            dependencies.add(path)
        }

        fun repository(path: String) {
            repositories.add(path)
        }

        fun onLoaded(callback: KotlinKernelHost.() -> Unit) {
            initCallbacks.add(DelegatedExecution(callback))
        }

        fun onShutdown(callback: KotlinKernelHost.() -> Unit) {
            shutdownCallbacks.add(DelegatedExecution(callback))
        }

        fun beforeCellExecution(callback: KotlinKernelHost.() -> Unit) {
            initCellCallbacks.add(DelegatedExecution(callback))
        }

        inline fun <reified T : Any> onVariable(noinline callback: VariableDeclarationCallback<T>) {
            val execution = FieldHandlerExecution(callback)
            addTypeConverter(FieldHandlerByClass(T::class, execution))
        }

        inline fun <reified T : Any> updateVariable(noinline callback: VariableUpdateCallback<T>) {
            val execution = FieldHandlerExecution<T> { host, value, property ->
                val tempField = callback(host, value, property)
                if (tempField != null) {
                    val valOrVar = if (property is KMutableProperty) "var" else "val"
                    val redeclaration = "$valOrVar ${property.name} = $tempField"
                    host.execute(redeclaration)
                }
            }
            addTypeConverter(FieldHandlerByClass(T::class, execution))
        }

        inline fun <reified T : Annotation> onClassAnnotation(noinline callback: ClassDeclarationsCallback) {
            addAnnotationHandler(AnnotationHandler(T::class, callback))
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
                annotations = annotations,
                resources = resources,
            )
    }

    override fun getDefinitions(notebook: Notebook<*>?): List<LibraryDefinition> {
        val builder = Builder()
        register(builder, notebook)
        return listOf(builder.getDefinition())
    }
}
