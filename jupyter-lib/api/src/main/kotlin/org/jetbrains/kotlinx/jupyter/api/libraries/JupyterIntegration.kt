package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.AfterCellExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ClassAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.ClassDeclarationsCallback
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.CodeCell
import org.jetbrains.kotlinx.jupyter.api.CodePreprocessor
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.FieldHandler
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.FileAnnotationCallback
import org.jetbrains.kotlinx.jupyter.api.FileAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.InternalVariablesMarker
import org.jetbrains.kotlinx.jupyter.api.InterruptionCallback
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.RendererFieldHandler
import org.jetbrains.kotlinx.jupyter.api.RendererHandler
import org.jetbrains.kotlinx.jupyter.api.RendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.ResultHandlerExecution
import org.jetbrains.kotlinx.jupyter.api.SubtypeRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.SubtypeThrowableRenderer
import org.jetbrains.kotlinx.jupyter.api.TextRenderer
import org.jetbrains.kotlinx.jupyter.api.TextRendererWithPriority
import org.jetbrains.kotlinx.jupyter.api.ThrowableRenderer
import org.jetbrains.kotlinx.jupyter.api.TypeName
import org.jetbrains.kotlinx.jupyter.api.VariableDeclarationCallback
import org.jetbrains.kotlinx.jupyter.api.VariableUpdateCallback
import org.jetbrains.kotlinx.jupyter.util.AcceptanceRule
import org.jetbrains.kotlinx.jupyter.util.NameAcceptanceRule
import kotlin.reflect.KProperty

/**
 * Base class for library integration with Jupyter Kernel via DSL
 * Derive from this class and pass registration callback into constructor
 */
abstract class JupyterIntegration : LibraryDefinitionProducer {

    abstract fun Builder.onLoaded()

    class Builder(val notebook: Notebook) {

        private val renderers = mutableListOf<RendererFieldHandler>()

        private val textRenderers = mutableListOf<TextRendererWithPriority>()

        private val throwableRenderers = mutableListOf<ThrowableRenderer>()

        private val init = mutableListOf<ExecutionCallback<*>>()

        private val beforeCellExecution = mutableListOf<ExecutionCallback<*>>()

        private val afterCellExecution = mutableListOf<AfterCellExecutionCallback>()

        private val shutdownCallbacks = mutableListOf<ExecutionCallback<*>>()

        private val converters = mutableListOf<FieldHandler>()

        private val classAnnotations = mutableListOf<ClassAnnotationHandler>()

        private val fileAnnotations = mutableListOf<FileAnnotationHandler>()

        private val resources = mutableListOf<LibraryResource>()

        private val imports = mutableListOf<String>()

        private val dependencies = mutableListOf<String>()

        private val repositories = mutableListOf<KernelRepository>()

        private val codePreprocessors = mutableListOf<CodePreprocessor>()

        private val internalVariablesMarkers = mutableListOf<InternalVariablesMarker>()

        private val integrationTypeNameRules = mutableListOf<AcceptanceRule<String>>()

        private val interruptionCallbacks = mutableListOf<InterruptionCallback>()

        private val colorSchemeChangedCallbacks = mutableListOf<ColorSchemeChangedCallback>()

        private var _minimalKernelVersion: KotlinKernelVersion? = null

        private val options: MutableMap<String, String> = mutableMapOf()

        private var website: String? = null
        private var description: String? = null

        fun addRenderer(handler: RendererFieldHandler) {
            renderers.add(handler)
        }

        // Left for ABI compatibility
        fun addRenderer(handler: RendererHandler) {
            renderers.add(handler)
        }

        // Left for ABI compatibility
        fun addRenderer(handler: RendererTypeHandler) {
            renderers.add(handler)
        }

        fun addTextRenderer(renderer: TextRenderer) {
            textRenderers.add(TextRendererWithPriority(renderer))
        }

        fun addThrowableRenderer(renderer: ThrowableRenderer) {
            throwableRenderers.add(renderer)
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

        fun addCodePreprocessor(preprocessor: CodePreprocessor) {
            codePreprocessors.add(preprocessor)
        }

        fun markVariableInternal(marker: InternalVariablesMarker) {
            internalVariablesMarkers.add(marker)
        }

        inline fun <reified T : Any> render(noinline renderer: CodeCell.(T) -> Any) {
            return renderWithHost { _, value: T -> renderer(this, value) }
        }

        inline fun <reified T : Any> renderWithHost(noinline renderer: CodeCell.(ExecutionHost, T) -> Any) {
            val execution = ResultHandlerExecution { host, property ->
                val currentCell = notebook.currentCell
                    ?: throw IllegalStateException("Current cell should not be null on renderer invocation")
                FieldValue(renderer(currentCell, host, property.value as T), null)
            }
            addRenderer(SubtypeRendererTypeHandler(T::class, execution))
        }

        inline fun <reified E : Throwable> renderThrowable(noinline renderer: (E) -> Any) {
            addThrowableRenderer(SubtypeThrowableRenderer(E::class, renderer))
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
            for (path in paths) {
                repository(path)
            }
        }

        fun addRepository(repository: KernelRepository) {
            repositories.add(repository)
        }

        fun repository(
            path: String,
            username: String? = null,
            password: String? = null,
        ) {
            addRepository(KernelRepository(path, username, password))
        }

        fun onLoaded(callback: KotlinKernelHost.() -> Unit) {
            init.add(callback)
        }

        fun onShutdown(callback: KotlinKernelHost.() -> Unit) {
            shutdownCallbacks.add(callback)
        }

        fun beforeCellExecution(callback: KotlinKernelHost.() -> Unit) {
            beforeCellExecution.add(callback)
        }

        fun afterCellExecution(callback: AfterCellExecutionCallback) {
            afterCellExecution.add(callback)
        }

        /**
         * Runs [callback] for every snippet property of compile-time subtype of type [T]
         *
         * [callback] gives access to both runtime value of the property and its [KProperty] object
         */
        inline fun <reified T : Any> onVariable(noinline callback: VariableDeclarationCallback<T>) {
            addTypeConverter(FieldHandlerFactory.createDeclareHandler(TypeDetection.COMPILE_TIME, callback))
        }

        /**
         * Runs [callback] for every snippet property of compile-time subtype of type [T]
         *
         * [callback] gives access to both runtime value of the property and its [KProperty] object
         *
         * [callback] should usually execute some code that:
         * - has non-Unit result and return the name of result field
         * - defines some variable and return its name
         *
         * Original variable will then be **reassigned** to this new name.
         *
         * For example:
         *
         * ```
         * updateVariable<MyType> { value, kProperty ->
         *     // MyWrapper class should be previously defined in the notebook
         *     execute("MyWrapper(${kProperty.name})").name
         * }
         * ```
         * or
         * ```
         * updateVariable<MyType> { value, kProperty ->
         *     // MyWrapper class should be previously defined in the notebook
         *     execute("val wrapper = MyWrapper(${kProperty.name})")
         *     return "wrapper"
         * }
         * ```
         */
        inline fun <reified T : Any> updateVariable(noinline callback: VariableUpdateCallback<T>) {
            addTypeConverter(FieldHandlerFactory.createUpdateHandler(TypeDetection.COMPILE_TIME, callback))
        }

        /**
         * Same as [onVariable], but based on runtime type that is figured out by reflection
         */
        inline fun <reified T : Any> onVariableByRuntimeType(noinline callback: VariableDeclarationCallback<T>) {
            addTypeConverter(FieldHandlerFactory.createDeclareHandler(TypeDetection.RUNTIME, callback))
        }

        /**
         * Same as [updateVariable], but based on runtime type that is figured out by reflection
         */
        inline fun <reified T : Any> updateVariableByRuntimeType(noinline callback: VariableUpdateCallback<T>) {
            addTypeConverter(FieldHandlerFactory.createUpdateHandler(TypeDetection.RUNTIME, callback))
        }

        inline fun <reified T : Annotation> onClassAnnotation(noinline callback: ClassDeclarationsCallback) {
            addClassAnnotationHandler(ClassAnnotationHandler(T::class, callback))
        }

        inline fun <reified T : Annotation> onFileAnnotation(noinline callback: FileAnnotationCallback) {
            addFileAnnotationHanlder(FileAnnotationHandler(T::class, callback))
        }

        fun preprocessCodeWithLibraries(callback: KotlinKernelHost.(Code) -> CodePreprocessor.Result) {
            addCodePreprocessor(
                object : CodePreprocessor {
                    override fun process(code: String, host: KotlinKernelHost): CodePreprocessor.Result {
                        return host.callback(code)
                    }
                },
            )
        }

        fun preprocessCode(callback: KotlinKernelHost.(Code) -> Code) {
            preprocessCodeWithLibraries { CodePreprocessor.Result(this.callback(it)) }
        }

        /**
         * All integrations transitively loaded by this integration will be tested against
         * passed acceptance rule and won't be loaded if the rule returned `false`.
         * If there were no acceptance rules that returned not-null values, integration
         * **will be loaded**. If there are several acceptance rules that returned not-null values,
         * the latest one will be taken into account.
         */
        fun addIntegrationTypeNameRule(rule: AcceptanceRule<TypeName>) {
            integrationTypeNameRules.add(rule)
        }

        /**
         * See [addIntegrationTypeNameRule]
         */
        fun acceptIntegrationTypeNameIf(predicate: (TypeName) -> Boolean) {
            addIntegrationTypeNameRule(NameAcceptanceRule(true, predicate))
        }

        /**
         * See [addIntegrationTypeNameRule]
         */
        fun discardIntegrationTypeNameIf(predicate: (TypeName) -> Boolean) {
            addIntegrationTypeNameRule(NameAcceptanceRule(false, predicate))
        }

        fun onInterrupt(action: InterruptionCallback) {
            interruptionCallbacks.add(action)
        }

        fun onColorSchemeChange(action: ColorSchemeChangedCallback) {
            colorSchemeChangedCallbacks.add(action)
        }

        fun setMinimalKernelVersion(version: KotlinKernelVersion) {
            _minimalKernelVersion = version
        }

        fun setMinimalKernelVersion(version: String) {
            setMinimalKernelVersion(KotlinKernelVersion.from(version) ?: error("Wrong kernel version format: $version"))
        }

        fun addOption(name: String, value: String) {
            options[name] = value
        }

        fun addOptions(options: Map<String, String>) {
            this.options.putAll(options)
        }

        fun setDescription(description: String) {
            this.description = description
        }

        fun setWebsite(website: String) {
            this.website = website
        }

        internal fun getDefinition() =
            libraryDefinition {
                it.description = description
                it.website = website
                it.options = options
                it.init = init
                it.renderers = renderers
                it.textRenderers = textRenderers
                it.throwableRenderers = throwableRenderers
                it.converters = converters
                it.imports = imports
                it.dependencies = dependencies
                it.repositories = repositories
                it.initCell = beforeCellExecution
                it.afterCellExecution = afterCellExecution
                it.shutdown = shutdownCallbacks
                it.classAnnotations = classAnnotations
                it.fileAnnotations = fileAnnotations
                it.resources = resources
                it.codePreprocessors = codePreprocessors
                it.internalVariablesMarkers = internalVariablesMarkers
                it.integrationTypeNameRules = integrationTypeNameRules
                it.interruptionCallbacks = interruptionCallbacks
                it.colorSchemeChangedCallbacks = colorSchemeChangedCallbacks
                it.minKernelVersion = _minimalKernelVersion
            }
    }

    override fun getDefinitions(notebook: Notebook): List<LibraryDefinition> {
        val builder = Builder(notebook)
        builder.onLoaded()
        return listOf(builder.getDefinition())
    }
}
