package org.jetbrains.kotlinx.jupyter

import jupyter.kotlin.CompilerArgs
import jupyter.kotlin.DependsOn
import jupyter.kotlin.KotlinContext
import jupyter.kotlin.KotlinKernelHostProvider
import jupyter.kotlin.Repository
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.Renderable
import org.jetbrains.kotlinx.jupyter.codegen.ClassAnnotationsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.ClassAnnotationsProcessorImpl
import org.jetbrains.kotlinx.jupyter.codegen.FieldsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.FieldsProcessorImpl
import org.jetbrains.kotlinx.jupyter.codegen.FileAnnotationsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.FileAnnotationsProcessorImpl
import org.jetbrains.kotlinx.jupyter.codegen.RenderersProcessorImpl
import org.jetbrains.kotlinx.jupyter.codegen.ResultsRenderersProcessor
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.compiler.CompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.DefaultCompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.JupyterScriptClassGetter
import org.jetbrains.kotlinx.jupyter.compiler.ScriptImportsCollector
import org.jetbrains.kotlinx.jupyter.compiler.util.Classpath
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScriptsData
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.config.getCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.dependencies.JupyterScriptDependenciesResolverImpl
import org.jetbrains.kotlinx.jupyter.dependencies.ResolverConfig
import org.jetbrains.kotlinx.jupyter.dependencies.ScriptDependencyAnnotationHandlerImpl
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.ReplException
import org.jetbrains.kotlinx.jupyter.exceptions.rethrowAsLibraryException
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesDir
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessorImpl
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResourcesProcessorImpl
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.magics.CompoundCodePreprocessor
import org.jetbrains.kotlinx.jupyter.magics.FullMagicsHandler
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.repl.CellExecutor
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater
import org.jetbrains.kotlinx.jupyter.repl.InternalEvaluator
import org.jetbrains.kotlinx.jupyter.repl.KotlinCompleter
import org.jetbrains.kotlinx.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlinx.jupyter.repl.impl.BaseKernelHost
import org.jetbrains.kotlinx.jupyter.repl.impl.CellExecutorImpl
import org.jetbrains.kotlinx.jupyter.repl.impl.InternalEvaluatorImpl
import org.jetbrains.kotlinx.jupyter.repl.impl.JupyterCompilerWithCompletion
import org.jetbrains.kotlinx.jupyter.repl.impl.ScriptImportsCollectorImpl
import org.jetbrains.kotlinx.jupyter.repl.impl.SharedReplContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KType
import kotlin.reflect.full.starProjectedType
import kotlin.script.experimental.api.KotlinType
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
import kotlin.script.experimental.api.refineConfigurationBeforeEvaluate
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.BasicJvmReplEvaluator
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm

data class EvalResult(
    val resultValue: Any?,
    val metadata: EvaluatedSnippetMetadata = EvaluatedSnippetMetadata.EMPTY
)

data class CheckResult(val isComplete: Boolean = true)

class ReplEvalRuntimeException(message: String, cause: Throwable? = null) : ReplException(message, cause)

enum class ExecutedCodeLogging {
    Off,
    All,
    Generated
}

interface ReplRuntimeProperties {
    val version: KotlinKernelVersion?
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
}

interface ReplForJupyter {

    fun eval(code: Code, displayHandler: DisplayHandler? = null, jupyterId: Int = -1): EvalResult

    fun <T> eval(execution: ExecutionCallback<T>): T

    fun evalOnShutdown(): List<EvalResult>

    fun checkComplete(code: Code): CheckResult

    suspend fun complete(code: Code, cursor: Int, callback: (CompletionResult) -> Unit)

    suspend fun listErrors(code: Code, callback: (ListErrorsResult) -> Unit)

    val homeDir: File?

    val currentClasspath: Collection<String>

    val resolverConfig: ResolverConfig?

    val runtimeProperties: ReplRuntimeProperties

    val resolutionInfoProvider: ResolutionInfoProvider

    var outputConfig: OutputConfig

    val notebook: NotebookImpl

    val fileExtension: String

    val isEmbedded: Boolean
        get() = false
}

fun <T> ReplForJupyter.execute(callback: ExecutionCallback<T>): T {
    return eval(callback)
}

class ReplForJupyterImpl(
    override val resolutionInfoProvider: ResolutionInfoProvider,
    scriptClasspath: List<File> = emptyList(),
    override val homeDir: File? = null,
    override val resolverConfig: ResolverConfig? = null,
    override val runtimeProperties: ReplRuntimeProperties = defaultRuntimeProperties,
    implicitReceivers: List<Any> = emptyList(),
    override val isEmbedded: Boolean = false,
) : ReplForJupyter, ReplOptions, BaseKernelHost, KotlinKernelHostProvider {

    constructor(
        config: KernelConfig,
        runtimeProperties: ReplRuntimeProperties,
        scriptReceivers: List<Any> = emptyList()
    ) :
        this(
            config.resolutionInfoProvider,
            config.scriptClasspath,
            config.homeDir,
            config.resolverConfig,
            runtimeProperties,
            scriptReceivers,
            config.embedded
        )

    private val scriptReceivers = HashMap<KType, Any>().apply {
        for (receiver in implicitReceivers) {
            putImplicitReceiver(receiver, receiver::class.starProjectedType)
        }
    }

    private val scriptReceiversList get() = scriptReceivers.values.toList()

    override val currentBranch: String
        get() = runtimeProperties.currentBranch
    override val librariesDir: File = homeDir?.resolve(LibrariesDir) ?: File(LibrariesDir)

    private val libraryInfoSwitcher = ResolutionInfoSwitcher.default(
        resolutionInfoProvider,
        librariesDir,
        currentBranch
    )

    private var outputConfigImpl = OutputConfig()

    private var currentKernelHost: KotlinKernelHost? = null

    override val notebook = NotebookImpl(runtimeProperties)

    private val librariesScanner = LibrariesScanner(notebook)
    private val resourcesProcessor = LibraryResourcesProcessorImpl()

    override var outputConfig
        get() = outputConfigImpl
        set(value) {
            // reuse output config instance, because it is already passed to CapturingOutputStream and stream parameters should be updated immediately
            outputConfigImpl.update(value)
        }

    override var trackClasspath: Boolean = false

    private var _executedCodeLogging: ExecutedCodeLogging = ExecutedCodeLogging.Off
    override var executedCodeLogging: ExecutedCodeLogging
        get() = _executedCodeLogging
        set(value) {
            _executedCodeLogging = value
            internalEvaluator.logExecution = value != ExecutedCodeLogging.Off
        }

    override var writeCompiledClasses: Boolean
        get() = internalEvaluator.writeCompiledClasses
        set(value) {
            internalEvaluator.writeCompiledClasses = value
        }

    private val resolver = JupyterScriptDependenciesResolverImpl(resolverConfig)

    private val beforeCellExecution = mutableListOf<ExecutionCallback<*>>()
    private val shutdownCodes = mutableListOf<ExecutionCallback<*>>()

    private val ctx = KotlinContext()

    private val compilerArgsConfigurator: CompilerArgsConfigurator = DefaultCompilerArgsConfigurator(
        runtimeProperties.jvmTargetForSnippets
    )

    private val magics = MagicsProcessor(
        FullMagicsHandler(
            this,
            LibrariesProcessorImpl(resolverConfig?.libraries, runtimeProperties.version),
            libraryInfoSwitcher,
        )
    )

    private val codePreprocessor = CompoundCodePreprocessor(magics)

    private val importsCollector: ScriptImportsCollector = ScriptImportsCollectorImpl()

    // Used for various purposes, i.e. completion and listing errors
    private val compilerConfiguration: ScriptCompilationConfiguration =
        getCompilationConfiguration(
            scriptClasspath,
            { scriptReceivers.keys.map(::KotlinType) },
            compilerArgsConfigurator,
            JupyterScriptClassGetter { jupyterCompiler.previousScriptsClasses.map(::KotlinType) },
            importsCollector = importsCollector
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
        if (!isEmbedded) {
            jvm {
                val filteringClassLoader = FilteringClassLoader(ClassLoader.getSystemClassLoader()) { fqn ->
                    listOf(
                        "jupyter.kotlin.",
                        "org.jetbrains.kotlinx.jupyter.api",
                        "kotlin."
                    ).any { fqn.startsWith(it) } ||
                        (fqn.startsWith("org.jetbrains.kotlin.") && !fqn.startsWith("org.jetbrains.kotlinx.jupyter."))
                }
                baseClassLoader(filteringClassLoader)
            }
        }
        constructorArgs(notebook, this@ReplForJupyterImpl)
        refineConfigurationBeforeEvaluate { context ->
            context.evaluationConfiguration.with {
                this.implicitReceivers.invoke(v = scriptReceiversList)
            }.asSuccess()
        }
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
        executedCodeLogging != ExecutedCodeLogging.Off
    )

    private val renderersProcessor: ResultsRenderersProcessor = run {
        val processor = RenderersProcessorImpl(contextUpdater)
        notebook.typeRenderersProcessor = processor
        processor
    }

    private val fieldsProcessor: FieldsProcessor = FieldsProcessorImpl(contextUpdater)

    private val classAnnotationsProcessor: ClassAnnotationsProcessor = ClassAnnotationsProcessorImpl()

    private val fileAnnotationsProcessor: FileAnnotationsProcessor = FileAnnotationsProcessorImpl(ScriptDependencyAnnotationHandlerImpl(resolver), compilerArgsConfigurator, jupyterCompiler, this)

    override fun checkComplete(code: String) = jupyterCompiler.checkComplete(code)

    internal val sharedContext = SharedReplContext(
        classAnnotationsProcessor,
        fileAnnotationsProcessor,
        fieldsProcessor,
        renderersProcessor,
        codePreprocessor,
        resourcesProcessor,
        librariesScanner,
        notebook,
        beforeCellExecution,
        shutdownCodes,
        scriptReceivers,
        internalEvaluator,
        this
    )

    private var evalContextEnabled = false
    private fun withEvalContext(action: () -> EvalResult): EvalResult {
        return synchronized(this) {
            evalContextEnabled = true
            try {
                action()
            } finally {
                evalContextEnabled = false
            }
        }
    }

    private val executor: CellExecutor = CellExecutorImpl(sharedContext)

    private fun onAnnotationsHandler(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        return if (evalContextEnabled) fileAnnotationsProcessor.process(context, currentKernelHost!!)
        else context.compilationConfiguration.asSuccess()
    }

    override fun eval(code: Code, displayHandler: DisplayHandler?, jupyterId: Int): EvalResult {
        return withEvalContext {
            rethrowAsLibraryException(LibraryProblemPart.BEFORE_CELL_CALLBACKS) {
                beforeCellExecution.forEach { executor.execute(it) }
            }

            var cell: CodeCellImpl? = null

            val compiledData: SerializedCompiledScriptsData
            val newImports: List<String>
            val result = try {
                executor.execute(code, displayHandler) { internalId, codeToExecute ->
                    cell = notebook.addCell(internalId, codeToExecute, EvalData(jupyterId, code))
                }
            } finally {
                compiledData = internalEvaluator.popAddedCompiledScripts()
                newImports = importsCollector.popAddedImports()
            }

            cell?.resultVal = result.result.value

            val rendered = result.result.let {
                log.catchAll {
                    renderersProcessor.renderResult(executor, it)
                }
            }?.let {
                log.catchAll {
                    if (it is Renderable) it.render(notebook) else it
                }
            }

            val newClasspath = log.catchAll {
                updateClasspath()
            } ?: emptyList()

            EvalResult(rendered, EvaluatedSnippetMetadata(newClasspath, compiledData, newImports))
        }
    }

    override fun <T> eval(execution: ExecutionCallback<T>): T {
        return synchronized(this) {
            executor.execute(execution)
        }
    }

    override fun evalOnShutdown(): List<EvalResult> {
        return shutdownCodes.map {
            val res = log.catchAll {
                rethrowAsLibraryException(LibraryProblemPart.SHUTDOWN) {
                    executor.execute(it)
                }
            }
            EvalResult(res)
        }
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

        val preprocessed = magics.processMagics(args.code, true).code
        return completer.complete(
            jupyterCompiler.completer,
            compilerConfiguration,
            args.code,
            preprocessed,
            jupyterCompiler.nextCounter(),
            args.cursor
        )
    }

    private val listErrorsQueue = LockQueue<ListErrorsResult, ListErrorsArgs>()
    override suspend fun listErrors(code: String, callback: (ListErrorsResult) -> Unit) =
        doWithLock(ListErrorsArgs(code, callback), listErrorsQueue, ListErrorsResult(code), ::doListErrors)

    private fun doListErrors(args: ListErrorsArgs): ListErrorsResult {
        if (looksLikeReplCommand(args.code)) return reportCommandErrors(args.code)

        val preprocessed = magics.processMagics(args.code, true).code

        val errorsList = jupyterCompiler.listErrors(preprocessed)

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
