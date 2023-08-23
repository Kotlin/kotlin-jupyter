package org.jetbrains.kotlinx.jupyter

import jupyter.kotlin.CompilerArgs
import jupyter.kotlin.DependsOn
import jupyter.kotlin.KotlinContext
import jupyter.kotlin.Repository
import jupyter.kotlin.generateHTMLVarsReport
import jupyter.kotlin.providers.UserHandlesProvider
import jupyter.kotlin.variablesReport
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
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
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.compiler.CompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.DefaultCompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.ScriptDeclarationsCollectorInternal
import org.jetbrains.kotlinx.jupyter.compiler.ScriptImportsCollector
import org.jetbrains.kotlinx.jupyter.compiler.util.Classpath
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScriptsData
import org.jetbrains.kotlinx.jupyter.config.catchAll
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
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptorsProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResourcesProcessorImpl
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.getDefaultResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.magics.CompletionMagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.CompoundCodePreprocessor
import org.jetbrains.kotlinx.jupyter.magics.ErrorsMagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.FullMagicsHandler
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.NoOpDisplayHandler
import org.jetbrains.kotlinx.jupyter.repl.CellExecutor
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater
import org.jetbrains.kotlinx.jupyter.repl.EvalResultEx
import org.jetbrains.kotlinx.jupyter.repl.InternalEvaluator
import org.jetbrains.kotlinx.jupyter.repl.InternalVariablesMarkersProcessor
import org.jetbrains.kotlinx.jupyter.repl.KotlinCompleter
import org.jetbrains.kotlinx.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlinx.jupyter.repl.ShutdownEvalResult
import org.jetbrains.kotlinx.jupyter.repl.impl.AfterCellExecutionsProcessor
import org.jetbrains.kotlinx.jupyter.repl.impl.BaseKernelHost
import org.jetbrains.kotlinx.jupyter.repl.impl.BeforeCellExecutionsProcessor
import org.jetbrains.kotlinx.jupyter.repl.impl.CellExecutorImpl
import org.jetbrains.kotlinx.jupyter.repl.impl.ColorSchemeChangeCallbacksProcessorImpl
import org.jetbrains.kotlinx.jupyter.repl.impl.InternalEvaluatorImpl
import org.jetbrains.kotlinx.jupyter.repl.impl.InternalVariablesMarkersProcessorImpl
import org.jetbrains.kotlinx.jupyter.repl.impl.InterruptionCallbacksProcessorImpl
import org.jetbrains.kotlinx.jupyter.repl.impl.JupyterCompilerWithCompletion
import org.jetbrains.kotlinx.jupyter.repl.impl.ScriptDeclarationsCollectorImpl
import org.jetbrains.kotlinx.jupyter.repl.impl.ScriptImportsCollectorImpl
import org.jetbrains.kotlinx.jupyter.repl.impl.SharedReplContext
import org.jetbrains.kotlinx.jupyter.repl.impl.ShutdownExecutionsProcessor
import org.jetbrains.kotlinx.jupyter.repl.postRender
import org.jetbrains.kotlinx.jupyter.repl.workflow.ExecutorWorkflowListener
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
import kotlin.script.experimental.dependencies.RepositoryCoordinates
import kotlin.script.experimental.jvm.BasicJvmReplEvaluator
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm

data class CheckResult(val isComplete: Boolean = true)

class EvalRequestData(
    val code: Code,
    val jupyterId: Int = -1,
    val storeHistory: Boolean = true,
    @Suppress("UNUSED")
    val isSilent: Boolean = false,
)

enum class ExecutedCodeLogging {
    OFF,
    ALL,
    GENERATED
}

interface ReplRuntimeProperties {
    val version: KotlinKernelVersion?
    @Deprecated("This parameter is meaningless, do not use")
    val librariesFormatVersion: Int
    val currentBranch: String
    val currentSha: String
    val jvmTargetForSnippets: String
}

interface ReplOptions {
    val currentBranch: String
    val librariesDir: File

    var trackClasspath: Boolean
    var executedCodeLogging: ExecutedCodeLogging
    var writeCompiledClasses: Boolean
    var outputConfig: OutputConfig
    val debugPort: Int?
}

interface ReplForJupyter {

    fun <T> eval(execution: ExecutionCallback<T>): T

    fun evalEx(evalData: EvalRequestData): EvalResultEx

    fun evalOnShutdown(): List<ShutdownEvalResult>

    fun checkComplete(code: Code): CheckResult

    suspend fun complete(code: Code, cursor: Int, callback: (CompletionResult) -> Unit)

    suspend fun listErrors(code: Code, callback: (ListErrorsResult) -> Unit)

    val homeDir: File?

    val currentClasspath: Collection<String>

    val currentClassLoader: ClassLoader

    val mavenRepositories: List<RepositoryCoordinates>

    val libraryResolver: LibraryResolver?

    val librariesScanner: LibrariesScanner

    val libraryDescriptorsProvider: LibraryDescriptorsProvider

    val runtimeProperties: ReplRuntimeProperties

    val resolutionInfoProvider: ResolutionInfoProvider

    val throwableRenderersProcessor: ThrowableRenderersProcessor

    var outputConfig: OutputConfig

    val notebook: MutableNotebook

    val displayHandler: DisplayHandler

    val fileExtension: String

    val isEmbedded: Boolean
        get() = false
}

class ReplForJupyterImpl(
    override val resolutionInfoProvider: ResolutionInfoProvider,
    override val displayHandler: DisplayHandler = NoOpDisplayHandler,
    private val scriptClasspath: List<File> = emptyList(),
    override val homeDir: File? = null,
    override val mavenRepositories: List<RepositoryCoordinates> = listOf(),
    override val libraryResolver: LibraryResolver? = null,
    override val runtimeProperties: ReplRuntimeProperties = defaultRuntimeProperties,
    private val scriptReceivers: List<Any> = emptyList(),
    override val isEmbedded: Boolean = false,
    override val notebook: MutableNotebook,
    override val librariesScanner: LibrariesScanner,
    override val debugPort: Int? = null
) : ReplForJupyter, ReplOptions, BaseKernelHost, UserHandlesProvider {

    override val currentBranch: String
        get() = runtimeProperties.currentBranch
    override val librariesDir: File = KERNEL_LIBRARIES.homeLibrariesDir(homeDir)

    private val libraryInfoSwitcher = getDefaultResolutionInfoSwitcher(
        resolutionInfoProvider,
        librariesDir,
        currentBranch
    )

    private val parseOutCellMagic = notebook.jupyterClientType == JupyterClientType.KOTLIN_NOTEBOOK

    private var outputConfigImpl = OutputConfig()

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

    override var outputConfig
        get() = outputConfigImpl
        set(value) {
            // reuse output config instance, because it is already passed to CapturingOutputStream and stream parameters should be updated immediately
            outputConfigImpl.update(value)
        }

    override var trackClasspath: Boolean = false

    private var _executedCodeLogging: ExecutedCodeLogging = ExecutedCodeLogging.OFF
    override var executedCodeLogging: ExecutedCodeLogging
        get() = _executedCodeLogging
        set(value) {
            _executedCodeLogging = value
            internalEvaluator.logExecution = value != ExecutedCodeLogging.OFF
        }

    override var writeCompiledClasses: Boolean
        get() = internalEvaluator.writeCompiledClasses
        set(value) {
            internalEvaluator.writeCompiledClasses = value
        }

    private val internalVariablesMarkersProcessor: InternalVariablesMarkersProcessor = InternalVariablesMarkersProcessorImpl()

    private val resolver: JupyterScriptDependenciesResolver = JupyterScriptDependenciesResolverImpl(mavenRepositories)

    private val ctx = KotlinContext()

    private val compilerArgsConfigurator: CompilerArgsConfigurator = DefaultCompilerArgsConfigurator(
        runtimeProperties.jvmTargetForSnippets
    )

    private val librariesProcessor: LibrariesProcessor = LibrariesProcessorImpl(libraryResolver, runtimeProperties.version)

    private val magics = MagicsProcessor(
        FullMagicsHandler(
            this,
            librariesProcessor,
            libraryInfoSwitcher,
        ),
        parseOutCellMagic
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
            scriptDataCollectors = listOf(importsCollector, declarationsCollector)
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
        executedCodeLogging != ExecutedCodeLogging.OFF,
        internalVariablesMarkersProcessor,
    )

    @Suppress("unused")
    private val debugUtilityProvider = DebugUtilityProvider(notebook)

    private val textRenderersProcessor: TextRenderersProcessorWithPreventingRecursion = TextRenderersProcessorImpl().apply {
        //registerDefaultRenderers()
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
    ).also {
        notebook.sharedReplContext = it
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

    @TestOnly
    @Suppress("unused")
    private fun printVariables(isHtmlFormat: Boolean = false) = log.debug(
        if (isHtmlFormat) generateHTMLVarsReport(notebook.variablesState) else notebook.variablesReport
    )

    @TestOnly
    @Suppress("unused")
    private fun printUsagesInfo(cellId: Int, usedVariables: Set<String>?) {
        log.debug(buildString {
            if (usedVariables.isNullOrEmpty()) {
                append("No usages for cell $cellId")
                return@buildString
            }
            append("Usages for cell $cellId:\n")
            usedVariables.forEach {
                append(it + "\n")
            }
        })
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
                executor.execute(evalData.code, displayHandler, currentCellId = evalData.jupyterId - 1, isUserCode = true, executorWorkflowListener = executorWorkflowListener)
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
     * Also, prints information about resolved libraries to stdout if [trackClasspath] is true
     *
     * @return Newly resolved classpath
     */
    private fun updateClasspath(): Classpath {
        val resolvedClasspath = resolver.popAddedClasspath().map { it.canonicalPath }
        if (resolvedClasspath.isEmpty()) return emptyList()

        val (oldClasspath, newClasspath) = resolvedClasspath.partition { it in currentClasspath }
        currentClasspath.addAll(newClasspath)
        if (trackClasspath) {
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
            ::doComplete
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
            args.cursor
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
        action: (Args) -> T
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
        override val callback: (CompletionResult) -> Unit
    ) : LockQueueArgs<CompletionResult>

    private data class ListErrorsArgs(val code: String, override val callback: (ListErrorsResult) -> Unit) :
        LockQueueArgs<ListErrorsResult>

    @JvmInline
    private value class LockQueue<T, Args : LockQueueArgs<T>>(
        private val args: AtomicReference<Args?> = AtomicReference()
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
