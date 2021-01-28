package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.AfterCellExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ClassAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.ClassDeclarationsCallback
import org.jetbrains.kotlinx.jupyter.api.FieldHandler
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerByClass
import org.jetbrains.kotlinx.jupyter.api.FieldHandlerExecution
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.FileAnnotationCallback
import org.jetbrains.kotlinx.jupyter.api.FileAnnotationHandler
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
abstract class JupyterIntegration : LibraryDefinitionProducer {

    abstract fun Builder.onLoaded(notebook: Notebook<*>?)

    class Builder {

        private val renderers = mutableListOf<RendererTypeHandler>()

        private val init = mutableListOf<Execution<*>>()

        private val beforeCellExecution = mutableListOf<Execution<*>>()

        private val afterCellExecution = mutableListOf<AfterCellExecutionCallback>()

        private val shutdownCallbacks = mutableListOf<Execution<*>>()

        private val converters = mutableListOf<FieldHandler>()

        private val classAnnotations = mutableListOf<ClassAnnotationHandler>()

        private val fileAnnotations = mutableListOf<FileAnnotationHandler>()

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

        fun addClassAnnotationHandler(handler: ClassAnnotationHandler) {
            classAnnotations.add(handler)
        }

        fun addFileAnnotationHanlder(handler: FileAnnotationHandler) {
            fileAnnotations.add(handler)
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

        fun import(vararg paths: String) {
            imports.addAll(paths)
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

        fun dependencies(vararg paths: String) {
            dependencies.addAll(paths)
        }

        fun repositories(vararg paths: String) {
            repositories.addAll(paths)
        }

        fun onLoaded(callback: KotlinKernelHost.() -> Unit) {
            init.add(DelegatedExecution(callback))
        }

        fun onShutdown(callback: KotlinKernelHost.() -> Unit) {
            shutdownCallbacks.add(DelegatedExecution(callback))
        }

        fun beforeCellExecution(callback: KotlinKernelHost.() -> Unit) {
            beforeCellExecution.add(DelegatedExecution(callback))
        }

        fun afterCellExecution(callback: AfterCellExecutionCallback) {
            afterCellExecution.add(callback)
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
            addClassAnnotationHandler(ClassAnnotationHandler(T::class, callback))
        }

        inline fun <reified T : Annotation> onFileAnnotation(noinline callback: FileAnnotationCallback) {
            addFileAnnotationHanlder(FileAnnotationHandler(T::class, callback))
        }

        internal fun getDefinition() =
            LibraryDefinitionImpl(
                init = init,
                renderers = renderers,
                converters = converters,
                imports = imports,
                dependencies = dependencies,
                repositories = repositories,
                initCell = beforeCellExecution,
                afterCellExecution = afterCellExecution,
                shutdown = shutdownCallbacks,
                classAnnotations = classAnnotations,
                fileAnnotations = fileAnnotations,
                resources = resources,
            )
    }

    override fun getDefinitions(notebook: Notebook<*>?): List<LibraryDefinition> {
        val builder = Builder()
        builder.onLoaded(notebook)
        return listOf(builder.getDefinition())
    }
}
