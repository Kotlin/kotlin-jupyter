package org.jetbrains.kotlinx.jupyter.repl.impl

import jupyter.kotlin.CompilerArgs
import jupyter.kotlin.DependsOn
import jupyter.kotlin.KotlinContext
import jupyter.kotlin.Repository
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import jupyter.kotlin.providers.KotlinKernelHostProvider
import jupyter.kotlin.providers.UserHandlesProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.K2ReplEvaluator
import org.jetbrains.kotlinx.jupyter.DebugUtilityProvider
import org.jetbrains.kotlinx.jupyter.HomeDirLibraryDescriptorsProvider
import org.jetbrains.kotlinx.jupyter.LibraryDescriptorsByResolutionProvider
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.InMemoryMimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.KernelRunMode
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResultEx
import org.jetbrains.kotlinx.jupyter.api.NullabilityEraser
import org.jetbrains.kotlinx.jupyter.api.ProcessingPriority
import org.jetbrains.kotlinx.jupyter.api.Renderable
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.api.SessionOptions
import org.jetbrains.kotlinx.jupyter.api.ThrowableRenderersProcessor
import org.jetbrains.kotlinx.jupyter.api.outputs.DisplayHandler
import org.jetbrains.kotlinx.jupyter.api.outputs.standardMetadataModifiers
import org.jetbrains.kotlinx.jupyter.closeIfPossible
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
import org.jetbrains.kotlinx.jupyter.codegen.ThrowableRenderersProcessorImpl
import org.jetbrains.kotlinx.jupyter.commands.doCommandCompletion
import org.jetbrains.kotlinx.jupyter.commands.reportCommandErrors
import org.jetbrains.kotlinx.jupyter.common.HttpClient
import org.jetbrains.kotlinx.jupyter.common.LibraryDescriptorsManager
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.compiler.CompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.DefaultCompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.ScriptDeclarationsCollectorInternal
import org.jetbrains.kotlinx.jupyter.compiler.ScriptImportsCollector
import org.jetbrains.kotlinx.jupyter.config.CellId
import org.jetbrains.kotlinx.jupyter.config.addBaseClass
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.config.getCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.dependencies.JupyterScriptDependenciesResolver
import org.jetbrains.kotlinx.jupyter.dependencies.JupyterScriptDependenciesResolverImpl
import org.jetbrains.kotlinx.jupyter.dependencies.ScriptDependencyAnnotationHandlerImpl
import org.jetbrains.kotlinx.jupyter.exceptions.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.exceptions.isInterruptedException
import org.jetbrains.kotlinx.jupyter.execution.ColorSchemeChangeCallbacksProcessor
import org.jetbrains.kotlinx.jupyter.execution.InterruptionCallbacksProcessor
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryReferenceParser
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResourcesProcessorImpl
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.magics.CompletionMagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.CompoundCodePreprocessor
import org.jetbrains.kotlinx.jupyter.magics.ErrorsMagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.LibrariesAwareMagicsHandler
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCount
import org.jetbrains.kotlinx.jupyter.messaging.NoOpDisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommHandler
import org.jetbrains.kotlinx.jupyter.messaging.comms.installCommHandler
import org.jetbrains.kotlinx.jupyter.messaging.comms.requireUniqueTargets
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import org.jetbrains.kotlinx.jupyter.registerDefaultRenderers
import org.jetbrains.kotlinx.jupyter.repl.BaseKernelHost
import org.jetbrains.kotlinx.jupyter.repl.ClasspathProvider
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater
import org.jetbrains.kotlinx.jupyter.repl.EvalData
import org.jetbrains.kotlinx.jupyter.repl.EvalRequestData
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata
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
import org.jetbrains.kotlinx.jupyter.repl.embedded.InMemoryReplResultsHolder
import org.jetbrains.kotlinx.jupyter.repl.execution.AfterCellExecutionsProcessor
import org.jetbrains.kotlinx.jupyter.repl.execution.BeforeCellExecutionsProcessor
import org.jetbrains.kotlinx.jupyter.repl.execution.CellExecutor
import org.jetbrains.kotlinx.jupyter.repl.execution.ExecutorWorkflowListener
import org.jetbrains.kotlinx.jupyter.repl.execution.ShutdownExecutionsProcessor
import org.jetbrains.kotlinx.jupyter.repl.logging.LoggingManager
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableCodeCell
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import org.jetbrains.kotlinx.jupyter.repl.postRender
import org.jetbrains.kotlinx.jupyter.repl.result.Classpath
import org.jetbrains.kotlinx.jupyter.repl.result.EvalResultEx
import org.jetbrains.kotlinx.jupyter.repl.result.InternalEvalResult
import org.jetbrains.kotlinx.jupyter.repl.result.InternalMetadata
import org.jetbrains.kotlinx.jupyter.repl.result.InternalMetadataImpl
import org.jetbrains.kotlinx.jupyter.repl.result.InternalReplResult
import org.jetbrains.kotlinx.jupyter.repl.result.SerializedCompiledScriptsData
import org.jetbrains.kotlinx.jupyter.repl.result.buildScriptsData
import java.io.Closeable
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.atomic.AtomicReference
import kotlin.script.experimental.api.CompiledSnippet
import kotlin.script.experimental.api.ReplEvaluator
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.BasicJvmReplEvaluator
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.KJvmEvaluatedSnippet
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm

typealias KernelReplEvaluator = ReplEvaluator<CompiledSnippet, KJvmEvaluatedSnippet>

class ReplForJupyterImpl(
    private val loggerFactory: KernelLoggerFactory,
    override val resolutionInfoProvider: ResolutionInfoProvider,
    override val displayHandler: DisplayHandler = NoOpDisplayHandler,
    private val scriptClasspath: List<File> = emptyList(),
    override val homeDir: File? = null,
    mavenRepositories: List<MavenRepositoryCoordinates> = listOf(),
    override val libraryResolver: LibraryResolver? = null,
    override val runtimeProperties: ReplRuntimeProperties = defaultRuntimeProperties,
    private val scriptReceivers: List<Any> = emptyList(),
    override val kernelRunMode: KernelRunMode,
    override val notebook: MutableNotebook,
    override val librariesScanner: LibrariesScanner,
    override val debugPort: Int? = null,
    commHandlers: List<CommHandler> = listOf(),
    httpClient: HttpClient,
    private val libraryDescriptorsManager: LibraryDescriptorsManager,
    private val libraryReferenceParser: LibraryReferenceParser,
    librariesProcessor: LibrariesProcessor,
    override val options: ReplOptions,
    override val sessionOptions: SessionOptions,
    override val loggingManager: LoggingManager,
    magicsHandler: LibrariesAwareMagicsHandler,
    private val inMemoryReplResultsHolder: InMemoryReplResultsHolder,
    override val compilerMode: ReplCompilerMode,
    extraCompilerArguments: List<String> = emptyList(),
) : ReplForJupyter,
    BaseKernelHost,
    UserHandlesProvider,
    Closeable {
    private val logger = loggerFactory.getLogger(this::class)
    private val parseOutCellMagic = notebook.jupyterClientType == JupyterClientType.KOTLIN_NOTEBOOK

    private val resourcesProcessor =
        LibraryResourcesProcessorImpl(
            loggerFactory,
            httpClient,
        )

    private val internalVariablesMarkersProcessor: InternalVariablesMarkersProcessor = InternalVariablesMarkersProcessorImpl()

    private val resolver: JupyterScriptDependenciesResolver =
        JupyterScriptDependenciesResolverImpl(
            loggerFactory,
            mavenRepositories,
            sessionOptions::resolveSources,
            sessionOptions::resolveMpp,
        )

    private val ctx = KotlinContext()

    private val compilerArgsConfigurator: CompilerArgsConfigurator =
        DefaultCompilerArgsConfigurator(
            runtimeProperties.jvmTargetForSnippets,
            extraCompilerArguments,
        )

    private val magics =
        MagicsProcessor(
            magicsHandler,
            parseOutCellMagic,
        )
    override val libraryDescriptorsProvider =
        run {
            val provider =
                HomeDirLibraryDescriptorsProvider(
                    loggerFactory,
                    homeDir,
                    libraryDescriptorsManager,
                )
            if (libraryResolver != null) {
                LibraryDescriptorsByResolutionProvider(provider, libraryResolver, libraryReferenceParser)
            } else {
                provider
            }
        }
    private val completionMagics =
        CompletionMagicsProcessor(
            loggerFactory,
            libraryDescriptorsProvider,
            parseOutCellMagic,
            httpClient,
        )
    private val errorsMagics =
        ErrorsMagicsProcessor(
            loggerFactory,
            parseOutCellMagic,
        )

    private val codePreprocessor = CompoundCodePreprocessor(magics)
    private val importsCollector: ScriptImportsCollector = ScriptImportsCollectorImpl()
    private val declarationsCollector: ScriptDeclarationsCollectorInternal = ScriptDeclarationsCollectorImpl()

    // Used for various purposes, i.e., completion and listing errors
    private val compilerConfiguration: ScriptCompilationConfiguration =
        getCompilationConfiguration(
            scriptClasspath,
            scriptReceivers,
            compilerArgsConfigurator,
            scriptDataCollectors = listOf(importsCollector, declarationsCollector),
            replCompilerMode = compilerMode,
            loggerFactory = loggerFactory,
            body = {
                addBaseClass<ScriptTemplateWithDisplayHelpers>()
                implicitReceivers(ScriptTemplateWithDisplayHelpers::class)
            },
        ).with {
            refineConfiguration {
                onAnnotations(DependsOn::class, Repository::class, CompilerArgs::class, handler = ::onAnnotationsHandler)
            }
        }

    override val fileExtension: String
        get() = compilerConfiguration[ScriptCompilationConfiguration.fileExtension]!!

    private val ScriptCompilationConfiguration.classpath
        get() =
            this[ScriptCompilationConfiguration.dependencies]
                ?.filterIsInstance<JvmDependency>()
                ?.flatMap { it.classpath }
                .orEmpty()

    override val currentClasspath = compilerConfiguration.classpath.map { it.canonicalPath }.toMutableSet()
    private val currentSources = mutableSetOf<String>()
    private val evaluatedSnippetsMetadata = mutableListOf<InternalMetadata>()

    private val allEvaluatedSnippetsMetadata: InternalMetadata get() {
        val allCompiledData =
            buildScriptsData {
                for (metadata in evaluatedSnippetsMetadata) {
                    addData(metadata.compiledData)
                }
            }
        val allImports = evaluatedSnippetsMetadata.flatMap { it.newImports }

        return InternalMetadataImpl(
            allCompiledData,
            allImports,
        )
    }

    override val currentSessionState: EvaluatedSnippetMetadata get() {
        return EvaluatedSnippetMetadata(
            currentClasspath.toList(),
            currentSources.toList(),
            allEvaluatedSnippetsMetadata,
        )
    }

    private val evaluatorConfiguration =
        ScriptEvaluationConfiguration {
            implicitReceivers.invoke(v = scriptReceivers)
            val intermediateClassLoader =
                kernelRunMode.createIntermediaryClassLoader(
                    ReplForJupyterImpl::class.java.classLoader,
                )
            if (intermediateClassLoader != null) {
                notebook.intermediateClassLoader = intermediateClassLoader
                jvm {
                    val scriptClassloader =
                        URLClassLoader(
                            scriptClasspath.map { it.toURI().toURL() }.toTypedArray(),
                            intermediateClassLoader,
                        )
                    baseClassLoader(scriptClassloader)
                }
            }
            implicitReceivers(ScriptTemplateWithDisplayHelpers(this@ReplForJupyterImpl))
        }

    private val jupyterCompiler: JupyterCompilerWithCompletion by lazy {
        when (compilerMode) {
            ReplCompilerMode.K1 -> {
                JupyterCompilerWithCompletion.createK1Compiler(
                    compilerConfiguration,
                    evaluatorConfiguration,
                )
            }
            ReplCompilerMode.K2 -> {
                JupyterCompilerWithCompletion.createK2Compiler(
                    compilerConfiguration,
                    evaluatorConfiguration,
                )
            }
        }
    }

    private val evaluator: KernelReplEvaluator by lazy {
        when (compilerMode) {
            ReplCompilerMode.K1 -> BasicJvmReplEvaluator()
            ReplCompilerMode.K2 -> K2ReplEvaluator()
        }
    }

    private val hostProvider =
        object : KotlinKernelHostProvider {
            override val host: KotlinKernelHost?
                get() = notebook.executionHost
        }

    private val completer = KotlinCompleter()

    private val contextUpdater =
        ContextUpdater(
            loggerFactory,
            ctx,
            evaluator,
        )

    private val internalEvaluator: InternalEvaluator =
        InternalEvaluatorImpl(
            this,
            loggerFactory,
            jupyterCompiler,
            evaluator,
            contextUpdater,
            internalVariablesMarkersProcessor,
            options::executedCodeLogging,
            sessionOptions::serializeScriptData,
            options::writeCompiledClasses,
        )

    @Suppress("unused")
    private val debugUtilityProvider = DebugUtilityProvider(notebook)

    private val textRenderersProcessor: TextRenderersProcessorWithPreventingRecursion =
        TextRenderersProcessorImpl().apply {
            // registerDefaultRenderers()
        }

    private val renderersProcessor: ResultsRenderersProcessor =
        RenderersProcessorImpl(contextUpdater).apply {
            registerDefaultRenderers()
        }

    override val currentClassLoader: ClassLoader get() = internalEvaluator.lastClassLoader

    override val throwableRenderersProcessor: ThrowableRenderersProcessor = ThrowableRenderersProcessorImpl()

    private val fieldsProcessor: FieldsProcessorInternal =
        FieldsProcessorImpl(contextUpdater).apply {
            register(NullabilityEraser, ProcessingPriority.LOWEST)
        }

    private val classAnnotationsProcessor: ClassAnnotationsProcessor = ClassAnnotationsProcessorImpl()

    private val fileAnnotationsProcessor: FileAnnotationsProcessor =
        FileAnnotationsProcessorImpl(
            ScriptDependencyAnnotationHandlerImpl(resolver),
            compilerArgsConfigurator,
            jupyterCompiler,
            hostProvider,
        )

    private val interruptionCallbacksProcessor: InterruptionCallbacksProcessor = InterruptionCallbacksProcessorImpl(hostProvider)

    private val colorSchemeChangeCallbacksProcessor: ColorSchemeChangeCallbacksProcessor = ColorSchemeChangeCallbacksProcessorImpl()

    private val beforeCellExecutionsProcessor = BeforeCellExecutionsProcessor()
    private val afterCellExecutionsProcessor = AfterCellExecutionsProcessor(loggerFactory)
    private val shutdownExecutionsProcessor = ShutdownExecutionsProcessor(loggerFactory)

    private val classpathProvider = ClasspathProvider { currentClasspath.toList() }

    override fun checkComplete(code: String) = jupyterCompiler.checkComplete(code)

    internal val sharedContext =
        SharedReplContext(
            loggerFactory,
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
            inMemoryReplResultsHolder,
            sessionOptions,
            classpathProvider,
        ).also {
            notebook.sharedReplContext = it
            commHandlers.requireUniqueTargets()
            commHandlers.forEach { handler -> installCommHandler(handler) }
        }

    private var evalContextEnabled = false

    private fun <T> withEvalContext(action: () -> T): T =
        synchronized(this) {
            require(!evalContextEnabled) { "Recursive execution is not supported" }
            evalContextEnabled = true
            try {
                action()
            } finally {
                evalContextEnabled = false
                ctx.cellExecutionFinished()
            }
        }

    private val executor: CellExecutor = CellExecutorImpl(sharedContext)

    private fun onAnnotationsHandler(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> =
        if (evalContextEnabled) {
            fileAnnotationsProcessor.process(context, hostProvider.host!!)
        } else {
            context.compilationConfiguration.asSuccess()
        }

    override fun evalEx(evalData: EvalRequestData): EvalResultEx =
        withEvalContext {
            evalExImpl(evalData)
        }

    private fun evalExImpl(evalData: EvalRequestData): EvalResultEx {
        beforeCellExecutionsProcessor.process(executor)

        val result = evaluateUserCode(evalData.code, evalData.executionCount)

        fun getMetadata() = calculateEvalMetadata(evalData.storeHistory, result.metadata)
        return when (result) {
            is InternalReplResult.Success -> {
                val rendered = renderResult(result, evalData)
                val displayValue =
                    logger.catchAll {
                        notebook.postRender(rendered)
                    }

                EvalResultEx.Success(
                    result.internalResult,
                    rendered,
                    displayValue,
                    getMetadata(),
                )
            }
            is InternalReplResult.Error -> {
                val error = result.error

                val isInterrupted = error.isInterruptedException()
                if (isInterrupted) {
                    EvalResultEx.Interrupted(getMetadata())
                } else {
                    val originalError = (error as? ReplEvalRuntimeException)?.cause
                    val renderedError =
                        logger.catchAll {
                            originalError?.let {
                                throwableRenderersProcessor.renderThrowable(originalError)
                            }
                        }

                    if (renderedError != null) {
                        val displayError =
                            logger.catchAll {
                                notebook.postRender(renderedError)
                            }
                        EvalResultEx.RenderedError(
                            error,
                            renderedError,
                            displayError,
                            getMetadata(),
                        )
                    } else {
                        EvalResultEx.Error(
                            error,
                            getMetadata(),
                        )
                    }
                }
            }
        }
    }

    private fun renderResult(
        result: InternalReplResult.Success,
        evalData: EvalRequestData,
    ): Any? =
        result.internalResult
            .let { internalResult ->
                logger.catchAll {
                    renderersProcessor.renderResult(executor, internalResult.result)
                }
            }?.let {
                logger.catchAll {
                    if (it is Renderable) it.render(notebook) else it
                }
            }?.let {
                logger.catchAll {
                    if (it is InMemoryMimeTypedResult) transformInMemoryResults(it, evalData) else it
                }
            }

    /**
     * If the render result is an in-memory value, we need to extract it from
     * the mimetype and put it into the `InMemoryReplResultsHolder`. Then we
     * construct a new DisplayResult where the in-memory value is replaced by
     * its `jupyterId`. This allows us to re-use the existing Jupyter protocol
     * infrastructure to send display results, but also allows a custom UI
     * component on the Client side to find the in-memory value
     * again by asking for it in the `InMemoryReplResultsHolder`.
     */
    private fun transformInMemoryResults(
        rendered: InMemoryMimeTypedResult,
        evalData: EvalRequestData,
    ): MimeTypedResultEx {
        val id = evalData.executionCount.toString()
        val inMemoryValue = rendered.inMemoryOutput.result
        inMemoryReplResultsHolder.setReplResult(id, inMemoryValue)
        val mimeData = rendered.fallbackResult + Pair(rendered.inMemoryOutput.mimeType, JsonPrimitive(id))
        return MimeTypedResultEx(Json.encodeToJsonElement(mimeData), null, standardMetadataModifiers())
    }

    private fun calculateEvalMetadata(
        storeHistory: Boolean,
        internalMetadata: InternalMetadata,
    ): EvaluatedSnippetMetadata {
        val newClasspath =
            logger.catchAll {
                updateClasspath()
            } ?: emptyList()

        val newSources =
            logger.catchAll {
                updateSources()
            } ?: emptyList()

        if (!storeHistory) {
            logger.catchAll { notebook.popCell() }
        }

        // send type information
        val variablesStateUpdate = notebook.variablesState.mapValues { it.value.property.toString() }
        return EvaluatedSnippetMetadata(newClasspath, newSources, internalMetadata, variablesStateUpdate)
    }

    private fun evaluateUserCode(
        code: Code,
        executionCount: ExecutionCount,
    ): InternalReplResult {
        val cell: MutableCodeCell = notebook.addCell(EvalData(executionCount, code))
        val compiledData: SerializedCompiledScriptsData
        val newImports: List<String>
        var throwable: Throwable? = null
        val internalResult: InternalEvalResult? =
            try {
                logger.debug("Current cell id: $executionCount")
                val executorWorkflowListener =
                    object : ExecutorWorkflowListener {
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
                    code,
                    isUserCode = true,
                    currentCellId = CellId.fromExecutionCount(executionCount),
                    executorWorkflowListener = executorWorkflowListener,
                )
            } catch (t: Throwable) {
                throwable = t
                null
            } finally {
                compiledData = internalEvaluator.popAddedCompiledScripts()
                newImports = importsCollector.popAddedImports()
            }

        if (internalResult != null) {
            cell.resultVal = internalResult.result.value
        }

        val metadata = InternalMetadataImpl(compiledData, newImports)
        evaluatedSnippetsMetadata.add(metadata)
        return if (throwable != null) {
            InternalReplResult.Error(throwable, metadata)
        } else {
            InternalReplResult.Success(internalResult!!, metadata)
        }
    }

    override fun <T> eval(execution: ExecutionCallback<T>): T =
        withEvalContext {
            executor.execute(execution)
        }

    override fun evalOnShutdown(): List<ShutdownEvalResult> = shutdownExecutionsProcessor.process(executor)

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

    override suspend fun complete(
        code: String,
        cursor: Int,
        callback: (CompletionResult) -> Unit,
    ) = doWithLock(
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

    override suspend fun listErrors(
        code: String,
        callback: (ListErrorsResult) -> Unit,
    ) = doWithLock(ListErrorsArgs(code, callback), listErrorsQueue, ListErrorsResult(code), ::doListErrors)

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

        val result =
            synchronized(this) {
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

    private data class ListErrorsArgs(
        val code: String,
        override val callback: (ListErrorsResult) -> Unit,
    ) : LockQueueArgs<ListErrorsResult>

    @JvmInline
    private value class LockQueue<T, Args : LockQueueArgs<T>>(
        private val args: AtomicReference<Args?> = AtomicReference(),
    ) {
        fun add(args: Args) {
            this.args.set(args)
        }

        fun get(): Args = args.get()!!
    }

    init {
        logger.info("Starting kotlin REPL engine. Compiler version: ${KotlinCompilerVersion.VERSION} (${compilerMode.name})")
        logger.info("Kernel version: ${runtimeProperties.version}")
        logger.info("Classpath used in script: $scriptClasspath")
    }

    override fun <T> withHost(
        currentHost: KotlinKernelHost,
        callback: () -> T,
    ): T {
        try {
            notebook.executionHost = currentHost
            return callback()
        } finally {
            notebook.executionHost = null
        }
    }

    override fun close() {
        notebook.closeIfPossible()
    }
}
