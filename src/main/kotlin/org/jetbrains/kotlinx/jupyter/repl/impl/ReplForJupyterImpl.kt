package org.jetbrains.kotlinx.jupyter.repl.impl

import jupyter.kotlin.CompilerArgs
import jupyter.kotlin.DependsOn
import jupyter.kotlin.KotlinContext
import jupyter.kotlin.Repository
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import jupyter.kotlin.providers.UserHandlesProvider
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlinx.jupyter.DebugUtilityProvider
import org.jetbrains.kotlinx.jupyter.HomeDirLibraryDescriptorsProvider
import org.jetbrains.kotlinx.jupyter.LibraryDescriptorsByResolutionProvider
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.NullabilityEraser
import org.jetbrains.kotlinx.jupyter.api.ProcessingPriority
import org.jetbrains.kotlinx.jupyter.api.Renderable
import org.jetbrains.kotlinx.jupyter.api.SessionOptions
import org.jetbrains.kotlinx.jupyter.codegen.ClassAnnotationsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.ClassAnnotationsProcessorImpl
import org.jetbrains.kotlinx.jupyter.codegen.FieldsProcessorImpl
import org.jetbrains.kotlinx.jupyter.codegen.FieldsProcessorInternal
import org.jetbrains.kotlinx.jupyter.codegen.FileAnnotationsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.FileAnnotationsProcessorImpl
import org.jetbrains.kotlinx.jupyter.codegen.RenderersProcessorImpl
import org.jetbrains.kotlinx.jupyter.codegen.ResultsRenderersProcessor
import org.jetbrains.kotlinx.jupyter.codegen.TextRenderersProcessorImpl
import org.jetbrains.kotlinx.jupyter.codegen.TextRenderersProcessorWithPreventingRecursion
import org.jetbrains.kotlinx.jupyter.codegen.ThrowableRenderersProcessor
import org.jetbrains.kotlinx.jupyter.codegen.ThrowableRenderersProcessorImpl
import org.jetbrains.kotlinx.jupyter.commands.doCommandCompletion
import org.jetbrains.kotlinx.jupyter.commands.reportCommandErrors
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.compiler.CompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.DefaultCompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.ScriptDeclarationsCollectorInternal
import org.jetbrains.kotlinx.jupyter.compiler.ScriptImportsCollector
import org.jetbrains.kotlinx.jupyter.compiler.util.Classpath
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScriptsData
import org.jetbrains.kotlinx.jupyter.config.addBaseClass
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.config.getCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.dependencies.JupyterScriptDependenciesResolver
import org.jetbrains.kotlinx.jupyter.dependencies.JupyterScriptDependenciesResolverImpl
import org.jetbrains.kotlinx.jupyter.dependencies.ScriptDependencyAnnotationHandlerImpl
import org.jetbrains.kotlinx.jupyter.execution.ColorSchemeChangeCallbacksProcessor
import org.jetbrains.kotlinx.jupyter.execution.InterruptionCallbacksProcessor
import org.jetbrains.kotlinx.jupyter.libraries.KERNEL_LIBRARIES
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessorImpl
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResourcesProcessorImpl
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.getDefaultResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.log
import org.jetbrains.kotlinx.jupyter.magics.CompletionMagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.CompoundCodePreprocessor
import org.jetbrains.kotlinx.jupyter.magics.ErrorsMagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.FullMagicsHandler
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.NoOpDisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommHandler
import org.jetbrains.kotlinx.jupyter.messaging.comms.installCommHandler
import org.jetbrains.kotlinx.jupyter.messaging.comms.requireUniqueTargets
import org.jetbrains.kotlinx.jupyter.registerDefaultRenderers
import org.jetbrains.kotlinx.jupyter.repl.BaseKernelHost
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater
import org.jetbrains.kotlinx.jupyter.repl.EvalData
import org.jetbrains.kotlinx.jupyter.repl.EvalRequestData
import org.jetbrains.kotlinx.jupyter.repl.EvalResultEx
import org.jetbrains.kotlinx.jupyter.repl.ExecutedCodeLogging
import org.jetbrains.kotlinx.jupyter.repl.InternalEvaluator
import org.jetbrains.kotlinx.jupyter.repl.InternalVariablesMarkersProcessor
import org.jetbrains.kotlinx.jupyter.repl.KotlinCompleter
import org.jetbrains.kotlinx.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.repl.ReplOptions
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.repl.SharedReplContext
import org.jetbrains.kotlinx.jupyter.repl.ShutdownEvalResult
import org.jetbrains.kotlinx.jupyter.repl.execution.AfterCellExecutionsProcessor
import org.jetbrains.kotlinx.jupyter.repl.execution.BeforeCellExecutionsProcessor
import org.jetbrains.kotlinx.jupyter.repl.execution.CellExecutor
import org.jetbrains.kotlinx.jupyter.repl.execution.ExecutorWorkflowListener
import org.jetbrains.kotlinx.jupyter.repl.execution.ShutdownExecutionsProcessor
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableCodeCell
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import org.jetbrains.kotlinx.jupyter.repl.postRender
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.atomic.AtomicReference
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.BasicJvmReplEvaluator
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm

class ReplForJupyterImpl(
    override val resolutionInfoProvider: ResolutionInfoProvider,
    override val displayHandler: DisplayHandler = NoOpDisplayHandler,
    private val scriptClasspath: List<File> = emptyList(),
    override val homeDir: File? = null,
    mavenRepositories: List<MavenRepositoryCoordinates> = listOf(),
    override val libraryResolver: LibraryResolver? = null,
    override val runtimeProperties: ReplRuntimeProperties = defaultRuntimeProperties,
    private val scriptReceivers: List<Any> = emptyList(),
    override val isEmbedded: Boolean = false,
    override val notebook: MutableNotebook,
    override val librariesScanner: LibrariesScanner,
    override val debugPort: Int? = null,
    commHandlers: List<CommHandler> = listOf(),
) : ReplForJupyter, BaseKernelHost, UserHandlesProvider {

    override val options: ReplOptions = ReplOptionsImpl { internalEvaluator }

    private val libraryInfoSwitcher = getDefaultResolutionInfoSwitcher(
        resolutionInfoProvider,
        KERNEL_LIBRARIES.homeLibrariesDir(homeDir),
        runtimeProperties.currentBranch,
    )

    private val parseOutCellMagic = notebook.jupyterClientType == JupyterClientType.KOTLIN_NOTEBOOK

    private var currentKernelHost: KotlinKernelHost? = null

    private val resourcesProcessor = LibraryResourcesProcessorImpl()

    override val sessionOptions: SessionOptions = object : SessionOptions {
        override var resolveSources: Boolean
            get() = resolver.resolveSources
            set(value) { resolver.resolveSources = value }

        override var resolveMpp: Boolean
            get() = resolver.resolveMpp
            set(value) { resolver.resolveMpp = value }

        override var serializeScriptData: Boolean
            get() = internalEvaluator.serializeScriptData
            set(value) { internalEvaluator.serializeScriptData = value }
    }

    private val internalVariablesMarkersProcessor: InternalVariablesMarkersProcessor = InternalVariablesMarkersProcessorImpl()

    private val resolver: JupyterScriptDependenciesResolver = JupyterScriptDependenciesResolverImpl(mavenRepositories)

    private val ctx = KotlinContext()

    private val compilerArgsConfigurator: CompilerArgsConfigurator = DefaultCompilerArgsConfigurator(
        runtimeProperties.jvmTargetForSnippets,
    )

    private val librariesProcessor: LibrariesProcessor = LibrariesProcessorImpl(libraryResolver, runtimeProperties.version)

    private val magics = MagicsProcessor(
        FullMagicsHandler(
            options,
            librariesProcessor,
            libraryInfoSwitcher,
        ),
        parseOutCellMagic,
    )
    override val libraryDescriptorsProvider = run {
        val provider = HomeDirLibraryDescriptorsProvider(homeDir)
        if (libraryResolver != null) {
            LibraryDescriptorsByResolutionProvider(provider, libraryResolver)
        } else {
            provider
        }
    }
    private val completionMagics = CompletionMagicsProcessor(libraryDescriptorsProvider, parseOutCellMagic)
    private val errorsMagics = ErrorsMagicsProcessor(parseOutCellMagic)

    private val codePreprocessor = CompoundCodePreprocessor(magics)

    private val importsCollector: ScriptImportsCollector = ScriptImportsCollectorImpl()
    private val declarationsCollector: ScriptDeclarationsCollectorInternal = ScriptDeclarationsCollectorImpl()

    // Used for various purposes, i.e. completion and listing errors
    private val compilerConfiguration: ScriptCompilationConfiguration =
        getCompilationConfiguration(
            scriptClasspath,
            scriptReceivers,
            compilerArgsConfigurator,
            scriptDataCollectors = listOf(importsCollector, declarationsCollector),
            body = { addBaseClass<ScriptTemplateWithDisplayHelpers>() },
        ).with {
            refineConfiguration {
                onAnnotations(DependsOn::class, Repository::class, CompilerArgs::class, handler = ::onAnnotationsHandler)
            }
        }

    override val fileExtension: String
        get() = compilerConfiguration[ScriptCompilationConfiguration.fileExtension]!!

    private val ScriptCompilationConfiguration.classpath
        get() = this[ScriptCompilationConfiguration.dependencies]
            ?.filterIsInstance<JvmDependency>()
            ?.flatMap { it.classpath }
            .orEmpty()

    override val currentClasspath = compilerConfiguration.classpath.map { it.canonicalPath }.toMutableSet()
    private val currentSources = mutableSetOf<String>()

    private class FilteringClassLoader(parent: ClassLoader, val includeFilter: (String) -> Boolean) :
        ClassLoader(parent) {
        override fun loadClass(name: String?, resolve: Boolean): Class<*> {
            val c = if (name != null && includeFilter(name)) {
                parent.loadClass(name)
            } else parent.parent.loadClass(name)
            if (resolve) {
                resolveClass(c)
            }
            return c
        }
    }

    private val evaluatorConfiguration = ScriptEvaluationConfiguration {
        implicitReceivers.invoke(v = scriptReceivers)
        if (!isEmbedded) {
            jvm {
                val filteringClassLoader = FilteringClassLoader(ClassLoader.getSystemClassLoader()) { fqn ->
                    listOf(
                        "jupyter.kotlin.",
                        "org.jetbrains.kotlinx.jupyter.api",
                        "kotlin.",
                        "kotlinx.serialization.",
                    ).any { fqn.startsWith(it) } ||
                        (fqn.startsWith("org.jetbrains.kotlin.") && !fqn.startsWith("org.jetbrains.kotlinx.jupyter."))
                }
                val scriptClassloader =
                    URLClassLoader(scriptClasspath.map { it.toURI().toURL() }.toTypedArray(), filteringClassLoader)
                baseClassLoader(scriptClassloader)
            }
        }
        constructorArgs(this@ReplForJupyterImpl)
    }

    private val jupyterCompiler by lazy {
        JupyterCompilerWithCompletion.create(compilerConfiguration, evaluatorConfiguration)
    }

    private val evaluator: BasicJvmReplEvaluator by lazy {
        BasicJvmReplEvaluator()
    }

    private val completer = KotlinCompleter()

    private val contextUpdater = ContextUpdater(ctx, evaluator)

    private val internalEvaluator: InternalEvaluator = InternalEvaluatorImpl(
        jupyterCompiler,
        evaluator,
        contextUpdater,
        options.executedCodeLogging != ExecutedCodeLogging.OFF,
        internalVariablesMarkersProcessor,
    )

    @Suppress("unused")
    private val debugUtilityProvider = DebugUtilityProvider(notebook)

    private val textRenderersProcessor: TextRenderersProcessorWithPreventingRecursion = TextRenderersProcessorImpl().apply {
        // registerDefaultRenderers()
    }

    private val renderersProcessor: ResultsRenderersProcessor = RenderersProcessorImpl(contextUpdater).apply {
        registerDefaultRenderers()
    }

    override val currentClassLoader: ClassLoader get() = internalEvaluator.lastClassLoader

    override val throwableRenderersProcessor: ThrowableRenderersProcessor = ThrowableRenderersProcessorImpl()

    private val fieldsProcessor: FieldsProcessorInternal = FieldsProcessorImpl(contextUpdater).apply {
        register(NullabilityEraser, ProcessingPriority.LOWEST)
    }

    private val classAnnotationsProcessor: ClassAnnotationsProcessor = ClassAnnotationsProcessorImpl()

    private val fileAnnotationsProcessor: FileAnnotationsProcessor = FileAnnotationsProcessorImpl(ScriptDependencyAnnotationHandlerImpl(resolver), compilerArgsConfigurator, jupyterCompiler, this)

    private val interruptionCallbacksProcessor: InterruptionCallbacksProcessor = InterruptionCallbacksProcessorImpl(this)

    private val colorSchemeChangeCallbacksProcessor: ColorSchemeChangeCallbacksProcessor = ColorSchemeChangeCallbacksProcessorImpl()

    private val beforeCellExecutionsProcessor = BeforeCellExecutionsProcessor()
    private val afterCellExecutionsProcessor = AfterCellExecutionsProcessor()
    private val shutdownExecutionsProcessor = ShutdownExecutionsProcessor()

    override fun checkComplete(code: String) = jupyterCompiler.checkComplete(code)

    internal val sharedContext = SharedReplContext(
        classAnnotationsProcessor,
        fileAnnotationsProcessor,
        fieldsProcessor,
        renderersProcessor,
        textRenderersProcessor,
        throwableRenderersProcessor,
        codePreprocessor,
        resourcesProcessor,
        librariesProcessor,
        librariesScanner,
        notebook,
        beforeCellExecutionsProcessor,
        shutdownExecutionsProcessor,
        afterCellExecutionsProcessor,
        internalEvaluator,
        this,
        internalVariablesMarkersProcessor,
        interruptionCallbacksProcessor,
        colorSchemeChangeCallbacksProcessor,
        displayHandler,
    ).also {
        notebook.sharedReplContext = it
        commHandlers.requireUniqueTargets()
        commHandlers.forEach { handler -> installCommHandler(handler) }
    }

    private var evalContextEnabled = false
    private fun <T> withEvalContext(action: () -> T): T {
        return synchronized(this) {
            evalContextEnabled = true
            try {
                action()
            } finally {
                evalContextEnabled = false
                ctx.cellExecutionFinished()
            }
        }
    }

    private val executor: CellExecutor = CellExecutorImpl(sharedContext)

    private fun onAnnotationsHandler(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        return if (evalContextEnabled) fileAnnotationsProcessor.process(context, currentKernelHost!!)
        else context.compilationConfiguration.asSuccess()
    }

    override fun evalEx(evalData: EvalRequestData): EvalResultEx {
        return withEvalContext {
            beforeCellExecutionsProcessor.process(executor)

            val cell: MutableCodeCell = notebook.addCell(EvalData(evalData))

            val compiledData: SerializedCompiledScriptsData
            val newImports: List<String>
            val result = try {
                log.debug("Current cell id: ${evalData.jupyterId}")
                val executorWorkflowListener = object : ExecutorWorkflowListener {
                    override fun internalIdGenerated(id: Int) {
                        cell.internalId = id
                    }

                    override fun codePreprocessed(preprocessedCode: Code) {
                        cell.preprocessedCode = preprocessedCode
                    }

                    override fun compilationFinished() {
                        cell.declarations = declarationsCollector.getLastSnippetDeclarations()
                    }
                }
                executor.execute(
                    evalData.code,
                    isUserCode = true,
                    currentCellId = evalData.jupyterId - 1,
                    executorWorkflowListener = executorWorkflowListener,
                )
            } finally {
                compiledData = internalEvaluator.popAddedCompiledScripts()
                newImports = importsCollector.popAddedImports()
            }
            cell.resultVal = result.result.value

            val rendered = result.result.let {
                log.catchAll {
                    renderersProcessor.renderResult(executor, it)
                }
            }?.let {
                log.catchAll {
                    if (it is Renderable) it.render(notebook) else it
                }
            }

            val displayValue = log.catchAll {
                notebook.postRender(rendered)
            }

            val newClasspath = log.catchAll {
                updateClasspath()
            } ?: emptyList()

            val newSources = log.catchAll {
                updateSources()
            } ?: emptyList()

            if (!evalData.storeHistory) {
                log.catchAll { notebook.popCell() }
            }

            val variablesStateUpdate = notebook.variablesState.mapValues { "" }
            EvalResultEx(
                result.result.value,
                rendered,
                displayValue,
                result.scriptInstance,
                result.result.name,
                EvaluatedSnippetMetadata(newClasspath, newSources, compiledData, newImports, variablesStateUpdate),
            )
        }
    }

    override fun <T> eval(execution: ExecutionCallback<T>): T {
        return synchronized(this) {
            executor.execute(execution)
        }
    }

    override fun evalOnShutdown(): List<ShutdownEvalResult> {
        return shutdownExecutionsProcessor.process(executor)
    }

    /**
     * Updates current classpath with newly resolved libraries paths
     * Also, prints information about resolved libraries to stdout if [ReplOptions.trackClasspath] is true
     *
     * @return Newly resolved classpath
     */
    private fun updateClasspath(): Classpath {
        val resolvedClasspath = resolver.popAddedClasspath().map { it.canonicalPath }
        if (resolvedClasspath.isEmpty()) return emptyList()

        val (oldClasspath, newClasspath) = resolvedClasspath.partition { it in currentClasspath }
        currentClasspath.addAll(newClasspath)
        if (options.trackClasspath) {
            val sb = StringBuilder()
            if (newClasspath.isNotEmpty()) {
                sb.appendLine("${newClasspath.count()} new paths were added to classpath:")
                newClasspath.sortedBy { it }.forEach { sb.appendLine(it) }
            }
            if (oldClasspath.isNotEmpty()) {
                sb.appendLine("${oldClasspath.count()} resolved paths were already in classpath:")
                oldClasspath.sortedBy { it }.forEach { sb.appendLine(it) }
            }
            sb.appendLine("Current classpath size: ${currentClasspath.count()}")
            println(sb.toString())
        }

        return newClasspath
    }

    private fun updateSources(): Classpath {
        val resolvedClasspath = resolver.popAddedSources().map { it.canonicalPath }
        val newClasspath = resolvedClasspath.filter { it !in currentSources }
        currentSources.addAll(newClasspath)
        return newClasspath
    }

    private val completionQueue = LockQueue<CompletionResult, CompletionArgs>()
    override suspend fun complete(code: String, cursor: Int, callback: (CompletionResult) -> Unit) =
        doWithLock(
            CompletionArgs(code, cursor, callback),
            completionQueue,
            CompletionResult.Empty(code, cursor),
            ::doComplete,
        )

    private fun doComplete(args: CompletionArgs): CompletionResult {
        if (looksLikeReplCommand(args.code)) return doCommandCompletion(args.code, args.cursor)

        val preprocessed = completionMagics.process(args.code, args.cursor)
        if (preprocessed.cursorInsideMagic) {
            return KotlinCompleter.getResult(args.code, args.cursor, preprocessed.completions)
        }

        return completer.complete(
            jupyterCompiler.completer,
            compilerConfiguration,
            args.code,
            preprocessed.code,
            jupyterCompiler.nextCounter(),
            args.cursor,
        )
    }

    private val listErrorsQueue = LockQueue<ListErrorsResult, ListErrorsArgs>()
    override suspend fun listErrors(code: String, callback: (ListErrorsResult) -> Unit) =
        doWithLock(ListErrorsArgs(code, callback), listErrorsQueue, ListErrorsResult(code), ::doListErrors)

    private fun doListErrors(args: ListErrorsArgs): ListErrorsResult {
        if (looksLikeReplCommand(args.code)) return reportCommandErrors(args.code)

        val preprocessingResult = errorsMagics.process(args.code)
        val errorsList = preprocessingResult.diagnostics + jupyterCompiler.listErrors(preprocessingResult.code)

        return ListErrorsResult(args.code, errorsList)
    }

    private fun <T, Args : LockQueueArgs<T>> doWithLock(
        args: Args,
        queue: LockQueue<T, Args>,
        default: T,
        action: (Args) -> T,
    ) {
        queue.add(args)

        val result = synchronized(this) {
            val lastArgs = queue.get()
            if (lastArgs !== args) {
                default
            } else {
                action(args)
            }
        }
        args.callback(result)
    }

    private interface LockQueueArgs<T> {
        val callback: (T) -> Unit
    }

    private data class CompletionArgs(
        val code: String,
        val cursor: Int,
        override val callback: (CompletionResult) -> Unit,
    ) : LockQueueArgs<CompletionResult>

    private data class ListErrorsArgs(val code: String, override val callback: (ListErrorsResult) -> Unit) :
        LockQueueArgs<ListErrorsResult>

    @JvmInline
    private value class LockQueue<T, Args : LockQueueArgs<T>>(
        private val args: AtomicReference<Args?> = AtomicReference(),
    ) {
        fun add(args: Args) {
            this.args.set(args)
        }

        fun get(): Args {
            return args.get()!!
        }
    }

    init {
        log.info("Starting kotlin REPL engine. Compiler version: ${KotlinCompilerVersion.VERSION}")
        log.info("Kernel version: ${runtimeProperties.version}")
        log.info("Classpath used in script: $scriptClasspath")
    }

    override fun <T> withHost(currentHost: KotlinKernelHost, callback: () -> T): T {
        try {
            currentKernelHost = currentHost
            return callback()
        } finally {
            currentKernelHost = null
        }
    }

    override val host: KotlinKernelHost?
        get() = currentKernelHost
}
