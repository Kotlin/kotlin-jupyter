package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.DependsOn
import jupyter.kotlin.KotlinContext
import org.jetbrains.kotlin.jupyter.api.KotlinKernelVersion
import jupyter.kotlin.Repository
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.jupyter.api.Code
import org.jetbrains.kotlin.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlin.jupyter.api.Renderable
import org.jetbrains.kotlin.jupyter.api.TypeHandler
import org.jetbrains.kotlin.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlin.jupyter.libraries.LibraryFactory
import org.jetbrains.kotlin.jupyter.libraries.buildDependenciesInitCode
import org.jetbrains.kotlin.jupyter.libraries.getDefinitions
import org.jetbrains.kotlin.jupyter.repl.ClassWriter
import org.jetbrains.kotlin.jupyter.repl.CompletionResult
import org.jetbrains.kotlin.jupyter.repl.ContextUpdater
import org.jetbrains.kotlin.jupyter.repl.KotlinCompleter
import org.jetbrains.kotlin.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlin.jupyter.repl.SourceCodeImpl
import org.jetbrains.kotlin.scripting.ide_services.compiler.KJvmReplCompilerWithIdeServices
import org.jetbrains.kotlin.scripting.resolve.skipExtensionsResolutionForImplicitsExceptInnermost
import java.io.File
import java.net.URLClassLoader
import java.util.LinkedList
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ReplAnalyzerResult
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.analysisDiagnostics
import kotlin.script.experimental.api.asDiagnostics
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.foundAnnotations
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.onSuccess
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.host.withDefaultsFrom
import kotlin.script.experimental.jvm.BasicJvmReplEvaluator
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.isError
import kotlin.script.experimental.jvm.util.isIncomplete
import kotlin.script.experimental.jvm.util.toSourceCodePosition
import kotlin.script.experimental.jvm.withUpdatedClasspath

typealias Classpath = List<String>

data class EvalResult(
    val resultValue: Any?,
    val newClasspath: Classpath,
)

data class CheckResult(val isComplete: Boolean = true)

open class ReplException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ReplEvalRuntimeException(message: String, cause: Throwable? = null) : ReplException(message, cause)

class ReplCompilerException(errorResult: ResultWithDiagnostics.Failure? = null, message: String? = null) :
    ReplException(message ?: errorResult?.getErrors() ?: "") {

    val firstDiagnostics = errorResult?.reports?.firstOrNull {
        it.severity == ScriptDiagnostic.Severity.ERROR || it.severity == ScriptDiagnostic.Severity.FATAL
    }

    constructor(message: String) : this(null, message)
}

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

    private val resolver = JupyterScriptDependenciesResolver(resolverConfig)

    private val typeRenderers = mutableMapOf<String, String>()

    private val initCellCodes = mutableListOf<String>()
    private val shutdownCodes = mutableListOf<String>()

    private fun renderResult(value: Any?, resultField: Pair<String, KotlinType>?): Any? {
        if (value == null || resultField == null) return null
        val rendererCode = typeRenderers[value.javaClass.canonicalName]
                ?: return if (value is Renderable) value.render(notebook) else value

        val code = rendererCode.replace("\$it", resultField.first)
        val result = doEval(code)
        return renderResult(result.value, result.resultField)
    }

    data class PreprocessingResult(
        val code: Code,
        val initCodes: List<Code>,
        val shutdownCodes: List<Code>,
        val initCellCodes: List<Code>,
        val typeRenderers: List<TypeHandler>,
    )

    fun preprocessCode(code: String): PreprocessingResult {

        val processedMagics = magics.processMagics(code)

        val initCodes = mutableListOf<Code>()
        val shutdownCodes = mutableListOf<Code>()
        val initCellCodes = mutableListOf<Code>()
        val typeRenderers = mutableListOf<TypeHandler>()
        val typeConverters = mutableListOf<TypeHandler>()
        val annotations = mutableListOf<TypeHandler>()

        processedMagics.libraries.getDefinitions(notebook).forEach { libraryDefinition ->
            libraryDefinition.buildDependenciesInitCode()?.let { initCodes.add(it) }
            typeRenderers.addAll(libraryDefinition.renderers)
            typeConverters.addAll(libraryDefinition.converters)
            annotations.addAll(libraryDefinition.annotations)
            initCellCodes.addAll(libraryDefinition.initCell)
            shutdownCodes.addAll(libraryDefinition.shutdown)
            libraryDefinition.init.forEach {

                // Library init code may contain other magics, so we process them recursively
                val preprocessed = preprocessCode(it)
                initCodes.addAll(preprocessed.initCodes)
                typeRenderers.addAll(preprocessed.typeRenderers)
                initCellCodes.addAll(preprocessed.initCellCodes)
                shutdownCodes.addAll(preprocessed.shutdownCodes)
                if (preprocessed.code.isNotBlank())
                    initCodes.add(preprocessed.code)
            }
        }

        val declarations = (typeConverters.map { typeProvidersProcessor.register(it) } + annotations.map { annotationsProcessor.register(it) })
            .joinToString("\n")
        if (declarations.isNotBlank()) {
            initCodes.add(declarations)
        }

        return PreprocessingResult(processedMagics.code, initCodes, shutdownCodes, initCellCodes, typeRenderers)
    }

    private val ctx = KotlinContext()

    private val magics = MagicsProcessor(this, LibrariesProcessor(resolverConfig?.libraries, runtimeProperties, libraryFactory))

    private fun configureMavenDepsOnAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotations = context.collectedData?.get(ScriptCollectedData.foundAnnotations)?.takeIf { it.isNotEmpty() }
            ?: return context.compilationConfiguration.asSuccess()
        val scriptContents = object : ScriptContents {
            override val annotations: Iterable<Annotation> = annotations
            override val file: File? = null
            override val text: CharSequence? = null
        }
        return try {
            resolver.resolveFromAnnotations(scriptContents)
                .onSuccess { classpath ->
                    context.compilationConfiguration
                        .let { if (classpath.isEmpty()) it else it.withUpdatedClasspath(classpath) }
                        .asSuccess()
                }
        } catch (e: Throwable) {
            ResultWithDiagnostics.Failure(e.asDiagnostics(path = context.script.locationId))
        }
    }

    private val compilerConfiguration by lazy {
        ScriptCompilationConfiguration {
            hostConfiguration.update { it.withDefaultsFrom(defaultJvmScriptingHostConfiguration) }
            baseClass.put(KotlinType(ScriptTemplateWithDisplayHelpers::class))
            fileExtension.put("jupyter.kts")

            val classImports = listOf(
                DependsOn::class,
                Repository::class,
                ScriptTemplateWithDisplayHelpers::class,
            ).map { it.java.name }
            defaultImports(classImports + defaultGlobalImports)

            jvm {
                updateClasspath(scriptClasspath)
            }
            refineConfiguration {
                onAnnotations(DependsOn::class, Repository::class, handler = { configureMavenDepsOnAnnotations(it) })
            }

            val receiversTypes = scriptReceivers.map { KotlinType(it.javaClass.canonicalName) }
            implicitReceivers(receiversTypes)
            skipExtensionsResolutionForImplicitsExceptInnermost(receiversTypes)

            compilerOptions(
                "-jvm-target",
                runtimeProperties.jvmTargetForSnippets,
                "-no-stdlib"
            )
        }
    }

    private val ScriptCompilationConfiguration.classpath
        get() = this[ScriptCompilationConfiguration.dependencies]
            ?.filterIsInstance<JvmDependency>()
            ?.flatMap { it.classpath }
            .orEmpty()

    override val currentClasspath = compilerConfiguration.classpath.map { it.canonicalPath }.toMutableSet()

    private class FilteringClassLoader(parent: ClassLoader, val includeFilter: (String) -> Boolean) : ClassLoader(parent) {
        override fun loadClass(name: String?, resolve: Boolean): Class<*> {
            val c = if (name != null && includeFilter(name))
                parent.loadClass(name)
            else parent.parent.loadClass(name)
            if (resolve)
                resolveClass(c)
            return c
        }
    }

    private val evaluatorConfiguration = ScriptEvaluationConfiguration {
        implicitReceivers.invoke(v = scriptReceivers)
        if (!embedded) {
            jvm {
                val filteringClassLoader = FilteringClassLoader(ClassLoader.getSystemClassLoader()) {fqn ->
                    listOf("jupyter.kotlin.", "org.jetbrains.kotlin.jupyter.api", "kotlin.").any { fqn.startsWith(it) } ||
                        (fqn.startsWith("org.jetbrains.kotlin.") && !fqn.startsWith("org.jetbrains.kotlin.jupyter."))
                }
                val scriptClassloader = URLClassLoader(scriptClasspath.map { it.toURI().toURL() }.toTypedArray(), filteringClassLoader)
                baseClassLoader(scriptClassloader)
            }
        }
        constructorArgs(notebook)
    }

    private var executionCounter = 0

    private val compiler: KJvmReplCompilerWithIdeServices by lazy {
        KJvmReplCompilerWithIdeServices()
    }

    private val evaluator: BasicJvmReplEvaluator by lazy {
        BasicJvmReplEvaluator()
    }

    private val completer = KotlinCompleter()

    private val contextUpdater = ContextUpdater(ctx, evaluator)

    private val typeProvidersProcessor: TypeProvidersProcessor = TypeProvidersProcessorImpl(contextUpdater)

    private val annotationsProcessor: AnnotationsProcessor = AnnotationsProcessorImpl(contextUpdater)

    private var currentDisplayHandler: DisplayHandler? = null

    private val scheduledExecutions = LinkedList<Code>()

    override fun checkComplete(code: String): CheckResult {
        val id = executionCounter++
        val codeLine = SourceCodeImpl(id, code)
        val result = runBlocking { compiler.analyze(codeLine, 0.toSourceCodePosition(codeLine), compilerConfiguration) }
        return when {
            result.isIncomplete() -> CheckResult(false)
            result.isError() -> throw ReplException(result.getErrors())
            else -> CheckResult(true)
        }
    }

    private fun executeInitCellCode() = initCellCodes.forEach(::evalNoReturn)

    private fun executeInitCode(initCodes: List<Code>) = initCodes.forEach(::evalNoReturn)

    private fun executeScheduledCode() {
        while (scheduledExecutions.isNotEmpty()) {
            val code = scheduledExecutions.pop()
            if (executedCodeLogging == ExecutedCodeLogging.Generated)
                println(code)
            evalNoReturn(code)
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
                if (executedCodeLogging == ExecutedCodeLogging.Generated)
                    println(it)
                evalNoReturn(it)
            }
        } while (codes.isNotEmpty())
    }

    private fun processAnnotations(replLine: Any?) {
        if (replLine == null) return
        log.catchAll {
            annotationsProcessor.process(replLine)
        }?.forEach {
            if (executedCodeLogging == ExecutedCodeLogging.Generated)
                println(it)
            evalNoReturn(it)
        }
    }

    private fun registerNewLibraries(p: PreprocessingResult) {
        p.initCellCodes.filter { !initCellCodes.contains(it) }.let(initCellCodes::addAll)
        p.shutdownCodes.filter { !shutdownCodes.contains(it) }.let(shutdownCodes::addAll)
        typeRenderers.putAll(p.typeRenderers.map { it.className to it.code })
    }

    private fun lastReplLine() = evaluator.lastEvaluatedSnippet?.get()?.result?.scriptInstance

    override fun eval(code: String, displayHandler: DisplayHandler?, jupyterId: Int): EvalResult {
        synchronized(this) {
            try {

                currentDisplayHandler = displayHandler

                executeInitCellCode()

                val preprocessed = preprocessCode(code)

                executeInitCode(preprocessed.initCodes)

                var result: Any? = null
                var resultField: Pair<String, KotlinType>? = null
                var replLine: Any? = null

                if (preprocessed.code.isNotBlank()) {
                    doEval(preprocessed.code, EvalData(jupyterId, code)).let {
                        result = it.value
                        resultField = it.resultField
                    }
                    replLine = lastReplLine()
                }

                log.catchAll {
                    processAnnotations(replLine)
                }

                log.catchAll {
                    executeScheduledCode()
                }

                log.catchAll {
                    registerNewLibraries(preprocessed)
                }

                log.catchAll {
                    processVariablesConversion()
                }

                log.catchAll {
                    executeScheduledCode()
                }

                log.catchAll {
                    updateOutputList(jupyterId, result)
                }

                val newClasspath = log.catchAll {
                    updateClasspath()
                } ?: emptyList()

                result = renderResult(result, resultField)


                return EvalResult(result, newClasspath)
            } finally {
                currentDisplayHandler = null
                scheduledExecutions.clear()
            }
        }
    }

    override fun evalOnShutdown(): List<EvalResult> {
        return shutdownCodes.map(::evalWithReturn)
    }

    private fun updateOutputList(jupyterId: Int, result: Any?) {
        if (jupyterId >= 0) {
            while (ReplOutputs.count() <= jupyterId) ReplOutputs.add(null)
            ReplOutputs[jupyterId] = result
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
        return completer.complete(compiler, compilerConfiguration, args.code, preprocessed, executionCounter++, args.cursor)
    }

    private val listErrorsQueue = LockQueue<ListErrorsResult, ListErrorsArgs>()
    override suspend fun listErrors(code: String, callback: (ListErrorsResult) -> Unit) =
        doWithLock(ListErrorsArgs(code, callback), listErrorsQueue, ListErrorsResult(code), ::doListErrors)

    private fun doListErrors(args: ListErrorsArgs): ListErrorsResult {
        if (isCommand(args.code)) return reportCommandErrors(args.code)

        val preprocessed = magics.processMagics(args.code, true).code
        val codeLine = SourceCodeImpl(executionCounter++, preprocessed)
        val errorsList = runBlocking { compiler.analyze(codeLine, 0.toSourceCodePosition(codeLine), compilerConfiguration) }
        return ListErrorsResult(args.code, errorsList.valueOrThrow()[ReplAnalyzerResult.analysisDiagnostics]!!)
    }

    private fun <T, Args : LockQueueArgs<T>> doWithLock(args: Args, queue: LockQueue<T, Args>, default: T, action: (Args) -> T) {
        queue.add(args)

        val result = synchronized(this) {
            val lastArgs = queue.get()
            if (lastArgs != args)
                default
            else
                action(args)
        }
        args.callback(result)
    }

    private fun evalNoReturn(code: String) {
        doEval(code)
        processAnnotations(lastReplLine())
    }

    // Result of this function is considered to be used for testing/debug purposes
    private fun evalWithReturn(code: String): EvalResult {
        val result = try {
            doEval(code)
        } catch (e: Exception) {
            InternalEvalResult(null, null)
        }
        processAnnotations(lastReplLine())
        return EvalResult(result.value, emptyList())
    }

    private data class InternalEvalResult(val value: Any?, val resultField: Pair<String, KotlinType>?)

    private interface LockQueueArgs <T> {
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
        if (executedCodeLogging == ExecutedCodeLogging.All)
            println(code)
        val id = executionCounter++
        val codeLine = SourceCodeImpl(id, code)

        val cell = if (evalData != null) {
            notebook.addCell(id, code, evalData)
        } else null

        when (val compileResultWithDiagnostics = runBlocking { compiler.compile(codeLine, compilerConfiguration) }) {
            is ResultWithDiagnostics.Success -> {
                val compileResult = compileResultWithDiagnostics.value
                classWriter?.writeClasses(codeLine, compileResult.get())
                val resultWithDiagnostics = runBlocking { evaluator.eval(compileResult, evaluatorConfiguration) }
                contextUpdater.update()

                when (resultWithDiagnostics) {
                    is ResultWithDiagnostics.Success -> {
                        val pureResult = resultWithDiagnostics.value.get()
                        return when (val resultValue = pureResult.result) {
                            is ResultValue.Error -> throw ReplEvalRuntimeException(resultValue.error.message.orEmpty(), resultValue.error)
                            is ResultValue.Unit -> {
                                InternalEvalResult(Unit, null)
                            }
                            is ResultValue.Value -> {
                                cell?.resultVal = resultValue.value
                                InternalEvalResult(resultValue.value, pureResult.compiledSnippet.resultField)
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
            is ResultWithDiagnostics.Failure -> throw ReplCompilerException(compileResultWithDiagnostics)
        }
    }

    init {
        log.info("Starting kotlin REPL engine. Compiler version: ${KotlinCompilerVersion.VERSION}")
        log.info("Classpath used in script: $scriptClasspath")
    }

    override fun display(value: Any) {
        currentDisplayHandler?.handleDisplay(value)
    }

    override fun updateDisplay(value: Any, id: String?) {
        currentDisplayHandler?.handleUpdate(value, id)
    }

    override fun scheduleExecution(code: Code) {
        scheduledExecutions.add(code)
    }

    override fun executeInit(codes: List<Code>) {
        executeInitCode(codes)
    }

    override fun execute(code: Code): Any? {
        val internalResult = doEval(code)
        return internalResult.value
    }
}
