package org.jetbrains.kotlinx.jupyter

import jupyter.kotlin.DependsOn
import jupyter.kotlin.KotlinContext
import jupyter.kotlin.Repository
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.HTML
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.Renderable
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.Execution
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.codegen.AnnotationsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.AnnotationsProcessorImpl
import org.jetbrains.kotlinx.jupyter.codegen.TypeProvidersProcessor
import org.jetbrains.kotlinx.jupyter.codegen.TypeProvidersProcessorImpl
import org.jetbrains.kotlinx.jupyter.codegen.TypeRenderersProcessor
import org.jetbrains.kotlinx.jupyter.codegen.TypeRenderersProcessorImpl
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.compiler.util.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.compiler.util.ReplException
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScriptsData
import org.jetbrains.kotlinx.jupyter.compiler.util.SourceCodeImpl
import org.jetbrains.kotlinx.jupyter.compiler.util.getErrors
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.config.getCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.dependencies.JupyterScriptDependenciesResolverImpl
import org.jetbrains.kotlinx.jupyter.dependencies.MavenDepsOnAnnotationsConfigurator
import org.jetbrains.kotlinx.jupyter.dependencies.ResolverConfig
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesDir
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryFactory
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResourcesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.getDefinitions
import org.jetbrains.kotlinx.jupyter.magics.FullMagicsHandler
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.repl.ClassWriter
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater
import org.jetbrains.kotlinx.jupyter.repl.KotlinCompleter
import org.jetbrains.kotlinx.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlinx.jupyter.repl.getCompilerWithCompletion
import java.io.File
import java.net.URLClassLoader
import java.util.LinkedList
import kotlin.reflect.KClass
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ReplAnalyzerResult
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.analysisDiagnostics
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.BasicJvmReplEvaluator
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.util.isError
import kotlin.script.experimental.jvm.util.isIncomplete
import kotlin.script.experimental.jvm.util.toSourceCodePosition

typealias Classpath = List<String>

data class EvalResult(
    val resultValue: Any?,
    val newClasspath: Classpath,
    val compiledData: SerializedCompiledScriptsData?,
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

    fun evalOnShutdown(): List<EvalResult>

    fun checkComplete(code: Code): CheckResult

    suspend fun complete(code: String, cursor: Int, callback: (CompletionResult) -> Unit)

    suspend fun listErrors(code: String, callback: (ListErrorsResult) -> Unit)

    val homeDir: File?

    val currentClasspath: Collection<String>

    val resolverConfig: ResolverConfig?

    val runtimeProperties: ReplRuntimeProperties

    val libraryFactory: LibraryFactory

    var outputConfig: OutputConfig

    val notebook: NotebookImpl

    val fileExtension: String
}

class ReplForJupyterImpl(
    override val libraryFactory: LibraryFactory,
    private val scriptClasspath: List<File> = emptyList(),
    override val homeDir: File? = null,
    override val resolverConfig: ResolverConfig? = null,
    override val runtimeProperties: ReplRuntimeProperties = defaultRuntimeProperties,
    private val scriptReceivers: List<Any> = emptyList(),
    private val embedded: Boolean = false,
) : ReplForJupyter, ReplOptions, KotlinKernelHost {

    constructor(config: KernelConfig, runtimeProperties: ReplRuntimeProperties, scriptReceivers: List<Any> = emptyList()) :
        this(config.libraryFactory, config.scriptClasspath, config.homeDir, config.resolverConfig, runtimeProperties, scriptReceivers, config.embedded)

    override val currentBranch: String
        get() = runtimeProperties.currentBranch
    override val librariesDir: File = homeDir?.resolve(LibrariesDir) ?: File(LibrariesDir)

    private var outputConfigImpl = OutputConfig()
    override val notebook = NotebookImpl(this, runtimeProperties)
    private val librariesScanner = LibrariesScanner()
    private val resourcesProcessor = LibraryResourcesProcessor()

    override var outputConfig
        get() = outputConfigImpl
        set(value) {
            // reuse output config instance, because it is already passed to CapturingOutputStream and stream parameters should be updated immediately
            outputConfigImpl.update(value)
        }

    override var trackClasspath: Boolean = false

    override var executedCodeLogging: ExecutedCodeLogging = ExecutedCodeLogging.Off

    private var classWriter: ClassWriter? = null

    override var writeCompiledClasses: Boolean
        get() = classWriter != null
        set(value) {
            classWriter = if (!value) null
            else {
                val cw = ClassWriter()
                System.setProperty("spark.repl.class.outputDir", cw.outputDir.toString())
                cw
            }
        }

    private val scriptsSerializer = CompiledScriptsSerializer()

    private val resolver = JupyterScriptDependenciesResolverImpl(resolverConfig)
    private val mavenDepsConfigurator = MavenDepsOnAnnotationsConfigurator(resolver)

    private val initCellCodes = mutableListOf<Execution>()
    private val shutdownCodes = mutableListOf<Execution>()

    private fun renderResult(value: Any?, resultFieldName: String?): Any? {
        val result = typeRenderersProcessor.renderResult(value, resultFieldName)
        return if (result is Renderable) result.render(notebook) else result
    }

    private fun preprocessCode(
        code: Code,
        builder: PreprocessingResultBuilder,
        addCodeAsInitCode: Boolean = false
    ) {
        val processedMagics = magics.processMagics(code)
        builder.addCode(processedMagics.code, addCodeAsInitCode)
        processedMagics.libraries.getDefinitions(notebook).forEach {
            builder.add(it)
        }
    }

    @TestOnly
    fun getPreprocessingResult(code: Code): PreprocessingResult {
        val builder = PreprocessingResultBuilder(typeProvidersProcessor, annotationsProcessor, typeRenderersProcessor, ::preprocessCode)
        preprocessCode(code, builder, false)
        return builder.build()
    }

    override fun addLibrary(definition: LibraryDefinition) {
        preprocessingResultBuilder.add(definition)
    }

    private val ctx = KotlinContext()

    private val magics = MagicsProcessor(FullMagicsHandler(this, LibrariesProcessor(resolverConfig?.libraries, runtimeProperties.version, libraryFactory)))

    // Used for various purposes, i.e. completion and listing errors
    private val lightCompilerConfiguration: ScriptCompilationConfiguration =
        getCompilationConfiguration(
            scriptClasspath,
            scriptReceivers,
            runtimeProperties.jvmTargetForSnippets,
        )

    // Used only for compilation
    private val compilerConfiguration: ScriptCompilationConfiguration = lightCompilerConfiguration.with {
        refineConfiguration {
            onAnnotations(DependsOn::class, Repository::class, handler = mavenDepsConfigurator::configure)
        }
    }

    override val fileExtension: String
        get() = lightCompilerConfiguration[ScriptCompilationConfiguration.fileExtension]!!

    private val ScriptCompilationConfiguration.classpath
        get() = this[ScriptCompilationConfiguration.dependencies]
            ?.filterIsInstance<JvmDependency>()
            ?.flatMap { it.classpath }
            .orEmpty()

    override val currentClasspath = lightCompilerConfiguration.classpath.map { it.canonicalPath }.toMutableSet()

    private class FilteringClassLoader(parent: ClassLoader, val includeFilter: (String) -> Boolean) : ClassLoader(parent) {
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
        if (!embedded) {
            jvm {
                val filteringClassLoader = FilteringClassLoader(ClassLoader.getSystemClassLoader()) { fqn ->
                    listOf("jupyter.kotlin.", "org.jetbrains.kotlinx.jupyter.api", "kotlin.").any { fqn.startsWith(it) } ||
                        (fqn.startsWith("org.jetbrains.kotlin.") && !fqn.startsWith("org.jetbrains.kotlinx.jupyter."))
                }
                val scriptClassloader = URLClassLoader(scriptClasspath.map { it.toURI().toURL() }.toTypedArray(), filteringClassLoader)
                baseClassLoader(scriptClassloader)
            }
        }
        constructorArgs(notebook)
    }

    private val jupyterCompiler by lazy {
        getCompilerWithCompletion(compilerConfiguration, evaluatorConfiguration)
    }

    private val evaluator: BasicJvmReplEvaluator by lazy {
        BasicJvmReplEvaluator()
    }

    private val completer = KotlinCompleter()

    private val contextUpdater = ContextUpdater(ctx, evaluator)

    private val typeRenderersProcessor: TypeRenderersProcessor = TypeRenderersProcessorImpl(this, contextUpdater)

    private val typeProvidersProcessor: TypeProvidersProcessor = TypeProvidersProcessorImpl(contextUpdater)

    private val annotationsProcessor: AnnotationsProcessor = AnnotationsProcessorImpl(contextUpdater)

    private var currentDisplayHandler: DisplayHandler? = null

    private val scheduledExecutions = LinkedList<Execution>()

    private val preprocessingResultBuilder = PreprocessingResultBuilder(
        typeProvidersProcessor,
        annotationsProcessor,
        typeRenderersProcessor,
        ::preprocessCode,
    )

    override fun checkComplete(code: String): CheckResult {
        val codeLine = jupyterCompiler.nextSourceCode(code)
        val result = runBlocking { jupyterCompiler.compiler.analyze(codeLine, 0.toSourceCodePosition(codeLine), lightCompilerConfiguration) }
        return when {
            result.isIncomplete() -> CheckResult(false)
            result.isError() -> throw ReplException(result.getErrors())
            else -> CheckResult(true)
        }
    }

    private fun executeInitCellCode() = initCellCodes.forEach { it.execute(this) }

    private fun executeScheduledCode() {
        while (scheduledExecutions.isNotEmpty()) {
            val code = scheduledExecutions.pop()
            if (executedCodeLogging == ExecutedCodeLogging.Generated) {
                println(code)
            }
            code.execute(this)
        }
    }

    private fun processVariablesConversion() {
        var iteration = 0
        do {
            if (iteration++ > 10) {
                log.error("Execution loop in type providers processing")
                break
            }
            val codes = typeProvidersProcessor.process()
            codes.forEach {
                if (executedCodeLogging == ExecutedCodeLogging.Generated) {
                    println(it)
                }
                execute(it)
            }
        } while (codes.isNotEmpty())
    }

    private fun processAnnotations(replLineClass: KClass<*>) {
        log.catchAll {
            annotationsProcessor.process(replLineClass)
        }?.let {
            if (executedCodeLogging == ExecutedCodeLogging.Generated) {
                println(it)
            }
            execute(it)
        }
    }

    private fun registerNewLibraries(p: PreprocessingResult) {
        p.initCellCodes.filter { !initCellCodes.contains(it) }.let(initCellCodes::addAll)
        p.shutdownCodes.filter { !shutdownCodes.contains(it) }.let(shutdownCodes::addAll)

        val classLoader = jupyterCompiler.lastClassLoader
        p.resources.forEach {
            val htmlText = resourcesProcessor.wrapLibrary(it, classLoader)
            currentDisplayHandler?.handleDisplay(HTML(htmlText))
        }
    }

    override fun eval(code: String, displayHandler: DisplayHandler?, jupyterId: Int): EvalResult {
        synchronized(this) {
            try {
                currentDisplayHandler = displayHandler

                executeInitCellCode()

                preprocessCode(code, preprocessingResultBuilder, false)
                val preprocessed = preprocessingResultBuilder.build()
                preprocessed.initCodes.forEach { it.execute(this) }
                preprocessingResultBuilder.clearInitCodes()

                var result: Any? = null
                var resultField: Pair<String, KotlinType>? = null
                var compiledData: SerializedCompiledScriptsData? = null

                if (preprocessed.code.isNotBlank()) {
                    doEval(preprocessed.code, EvalData(jupyterId, code)).let {
                        result = it.value
                        resultField = it.resultField
                        compiledData = it.compiledData
                    }

                    log.catchAll {
                        processAnnotations(jupyterCompiler.lastKClass)
                    }
                }

                log.catchAll {
                    executeScheduledCode()
                }

                log.catchAll {
                    librariesScanner.addLibrariesFromClassLoader(jupyterCompiler.lastClassLoader, notebook)
                }

                log.catchAll {
                    val newPreprocessedResult = preprocessingResultBuilder.build()
                    newPreprocessedResult.initCodes.forEach { it.execute(this) }
                    registerNewLibraries(newPreprocessedResult)
                    preprocessingResultBuilder.clear()
                }

                log.catchAll {
                    processVariablesConversion()
                }

                log.catchAll {
                    executeScheduledCode()
                }

                val newClasspath = log.catchAll {
                    updateClasspath()
                } ?: emptyList()

                result = renderResult(result, resultField?.first)

                return EvalResult(result, newClasspath, compiledData)
            } finally {
                currentDisplayHandler = null
                scheduledExecutions.clear()
            }
        }
    }

    override fun evalOnShutdown(): List<EvalResult> {
        return shutdownCodes.map(::evalWithReturn)
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
            if (newClasspath.count() > 0) {
                sb.appendLine("${newClasspath.count()} new paths were added to classpath:")
                newClasspath.sortedBy { it }.forEach { sb.appendLine(it) }
            }
            if (oldClasspath.count() > 0) {
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
        doWithLock(CompletionArgs(code, cursor, callback), completionQueue, CompletionResult.Empty(code, cursor), ::doComplete)

    private fun doComplete(args: CompletionArgs): CompletionResult {
        if (isCommand(args.code)) return doCommandCompletion(args.code, args.cursor)

        val preprocessed = magics.processMagics(args.code, true).code
        return completer.complete(jupyterCompiler.compiler, lightCompilerConfiguration, args.code, preprocessed, jupyterCompiler.nextCounter(), args.cursor)
    }

    private val listErrorsQueue = LockQueue<ListErrorsResult, ListErrorsArgs>()
    override suspend fun listErrors(code: String, callback: (ListErrorsResult) -> Unit) =
        doWithLock(ListErrorsArgs(code, callback), listErrorsQueue, ListErrorsResult(code), ::doListErrors)

    private fun doListErrors(args: ListErrorsArgs): ListErrorsResult {
        if (isCommand(args.code)) return reportCommandErrors(args.code)

        val preprocessed = magics.processMagics(args.code, true).code
        val codeLine = SourceCodeImpl(jupyterCompiler.nextCounter(), preprocessed)
        val errorsList = runBlocking { jupyterCompiler.compiler.analyze(codeLine, 0.toSourceCodePosition(codeLine), lightCompilerConfiguration) }
        return ListErrorsResult(args.code, errorsList.valueOrThrow()[ReplAnalyzerResult.analysisDiagnostics]!!)
    }

    private fun <T, Args : LockQueueArgs<T>> doWithLock(args: Args, queue: LockQueue<T, Args>, default: T, action: (Args) -> T) {
        queue.add(args)

        val result = synchronized(this) {
            val lastArgs = queue.get()
            if (lastArgs != args) {
                default
            } else {
                action(args)
            }
        }
        args.callback(result)
    }

    // Result of this function is considered to be used for testing/debug purposes
    private fun evalWithReturn(execution: Execution): EvalResult {
        val result = try {
            if (execution is CodeExecution) doEval(execution.code).value
            else execution.execute(this)
        } catch (e: Exception) {
            null
        }
        processAnnotations(jupyterCompiler.lastKClass)
        return EvalResult(result, emptyList(), null)
    }

    private data class InternalEvalResult(
        val value: Any?,
        val resultField: Pair<String, KotlinType>?,
        val compiledData: SerializedCompiledScriptsData? = null,
    )

    private interface LockQueueArgs<T> {
        val callback: (T) -> Unit
    }

    private data class CompletionArgs(val code: String, val cursor: Int, override val callback: (CompletionResult) -> Unit) : LockQueueArgs<CompletionResult>
    private data class ListErrorsArgs(val code: String, override val callback: (ListErrorsResult) -> Unit) : LockQueueArgs<ListErrorsResult>

    private class LockQueue<T, Args : LockQueueArgs<T>> {
        private var args: Args? = null

        fun add(args: Args) {
            synchronized(this) {
                this.args = args
            }
        }

        fun get(): Args {
            return args!!
        }
    }

    private fun doEval(code: String, evalData: EvalData? = null): InternalEvalResult {
        if (executedCodeLogging == ExecutedCodeLogging.All) {
            println("Executing:\n" + code)
        }
        val id = jupyterCompiler.nextCounter()
        val codeLine = SourceCodeImpl(id, code)

        val cell = if (evalData != null) {
            notebook.addCell(id, code, evalData)
        } else null

        val (compileResult, evalConfig) = jupyterCompiler.compileSync(codeLine)
        val compiledScript = compileResult.get()

        classWriter?.writeClasses(codeLine, compiledScript)
        val resultWithDiagnostics = runBlocking { evaluator.eval(compileResult, evalConfig) }
        contextUpdater.update()

        when (resultWithDiagnostics) {
            is ResultWithDiagnostics.Success -> {
                val pureResult = resultWithDiagnostics.value.get()
                return when (val resultValue = pureResult.result) {
                    is ResultValue.Error -> throw ReplEvalRuntimeException(resultValue.error.message.orEmpty(), resultValue.error)
                    is ResultValue.Unit -> {
                        InternalEvalResult(Unit, null, scriptsSerializer.serialize(compiledScript))
                    }
                    is ResultValue.Value -> {
                        cell?.resultVal = resultValue.value
                        InternalEvalResult(
                            resultValue.value,
                            pureResult.compiledSnippet.resultField,
                            scriptsSerializer.serialize(compiledScript)
                        )
                    }
                    is ResultValue.NotEvaluated -> {
                        throw ReplEvalRuntimeException(
                            buildString {
                                val cause = resultWithDiagnostics.reports.firstOrNull()?.exception
                                val stackTrace = cause?.stackTrace.orEmpty()
                                append("This snippet was not evaluated: ")
                                appendLine(cause.toString())
                                for (s in stackTrace)
                                    appendLine(s)
                            }
                        )
                    }
                    else -> throw IllegalStateException("Unknown eval result type $this")
                }
            }
            is ResultWithDiagnostics.Failure -> {
                throw ReplCompilerException(resultWithDiagnostics)
            }
            else -> throw IllegalStateException("Unknown result")
        }
    }

    init {
        log.info("Starting kotlin REPL engine. Compiler version: ${KotlinCompilerVersion.VERSION}")
        log.info("Kernel version: ${runtimeProperties.version}")
        log.info("Classpath used in script: $scriptClasspath")
    }

    override fun display(value: Any) {
        currentDisplayHandler?.handleDisplay(value)
    }

    override fun updateDisplay(value: Any, id: String?) {
        currentDisplayHandler?.handleUpdate(value, id)
    }

    override fun scheduleExecution(execution: Execution) {
        scheduledExecutions.add(execution)
    }

    override fun executeInit(codes: List<Code>) {
        codes.forEach(::execute)
    }

    override fun execute(code: Code): Any? {
        val internalResult = doEval(code)
        processAnnotations(jupyterCompiler.lastKClass)
        return internalResult.value
    }

    override fun executeInternal(code: Code): KotlinKernelHost.Result {
        val internalResult = doEval(code)
        return KotlinKernelHost.Result(internalResult.value, internalResult.resultField?.first)
    }
}
