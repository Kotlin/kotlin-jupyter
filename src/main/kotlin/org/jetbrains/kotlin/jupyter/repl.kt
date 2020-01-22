package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.*
import jupyter.kotlin.KotlinContext
import jupyter.kotlin.KotlinReceiver
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.jupyter.repl.completion.CompletionResult
import org.jetbrains.kotlin.jupyter.repl.completion.CompletionResultEmpty
import org.jetbrains.kotlin.jupyter.repl.completion.KotlinCompleter
import org.jetbrains.kotlin.jupyter.repl.reflect.ContextUpdater
import org.jetbrains.kotlin.jupyter.repl.reflect.lines
import org.jetbrains.kotlin.jupyter.repl.spark.ClassWriter
import java.io.File
import java.net.URLClassLoader
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.withDefaultsFrom
import kotlin.script.experimental.jvm.*
import kotlin.script.experimental.jvmhost.repl.JvmReplCompiler
import kotlin.script.experimental.jvmhost.repl.JvmReplEvaluator

data class EvalResult(val resultValue: Any?)

data class CheckResult(val codeLine: LineId, val isComplete: Boolean = true)

open class ReplException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ReplEvalRuntimeException(val errorResult: ReplEvalResult.Error.Runtime) : ReplException(errorResult.message, errorResult.cause)

val ReplCompileResult.Error.exceptionMessage: String
    get() {
        if (location == null)
            return message
        val prefix = with(location!!) {
            val start = if (lineStart != -1 || columnStart != -1) "($lineStart:$columnStart) " else ""
            val end = if (lineEnd != -1 || columnEnd != -1) "($lineEnd:$columnEnd) " else ""
            start + if (end.isNotEmpty()) "- $end" else ""
        }
        return prefix + message
    }

class ReplCompilerException(val errorResult: ReplCompileResult.Error) : ReplException(errorResult.exceptionMessage) {
    constructor (checkResult: ReplCheckResult.Error) : this(ReplCompileResult.Error(checkResult.message, checkResult.location))
    constructor (incompleteResult: ReplCompileResult.Incomplete) : this(ReplCompileResult.Error("Incomplete Code", null))
    constructor (checkResult: ReplEvalResult.Error.CompileTime) : this(ReplCompileResult.Error(checkResult.message, checkResult.location))
    constructor (incompleteResult: ReplEvalResult.Incomplete) : this(ReplCompileResult.Error("Incomplete Code", null))
    constructor (historyMismatchResult: ReplEvalResult.HistoryMismatch) : this(ReplCompileResult.Error("History Mismatch", CompilerMessageLocation.create(null, historyMismatchResult.lineNo, 0, null)))
    constructor (message: String) : this(ReplCompileResult.Error(message, null))
}

enum class ExecutedCodeLogging {
    Off,
    All,
    Generated
}

interface ReplOptions {
    var trackClasspath: Boolean

    var executedCodeLogging: ExecutedCodeLogging

    var writeCompiledClasses: Boolean

    var outputConfig: OutputConfig
}

typealias MethodName = String
typealias TypeName = String
typealias Code = String
typealias FieldName = String

interface ReplForJupyter {
    fun eval(code: Code, displayHandler: ((Any) -> Unit)? = null, jupyterId: Int = -1): EvalResult

    fun checkComplete(executionNumber: Long, code: Code): CheckResult

    fun complete(code: String, cursor: Int): CompletionResult

    val currentClasspath: Collection<String> get

    val resolverConfig: ResolverConfig? get

    var outputConfig: OutputConfig get
}

class ReplForJupyterImpl(val scriptClasspath: List<File> = emptyList(),
                         override val resolverConfig: ResolverConfig? = null, vararg scriptReceivers: Any) : ReplForJupyter, ReplOptions, KotlinKernelHost {

    var outputConfigImpl = OutputConfig()

    override var outputConfig
        get() = outputConfigImpl
        set(value) {
            // reuse output config instance, because it is already passed to CapturingOutputStream and stream parameters should be updated immediately
            outputConfigImpl.update(value)
        }

    override var trackClasspath: Boolean = false

    override var executedCodeLogging: ExecutedCodeLogging = ExecutedCodeLogging.Off

    var classWriter: ClassWriter? = null

    override var writeCompiledClasses: Boolean
        get() = classWriter != null
        set(value) {
            if (!value) classWriter = null
            else {
                val cw = ClassWriter()
                System.setProperty("spark.repl.class.outputDir", cw.outputDir.toString())
                classWriter = cw
            }
        }

    private val resolver = JupyterScriptDependenciesResolver(resolverConfig)

    private val typeRenderers = mutableMapOf<String, String>()

    private val initCellCodes = mutableListOf<String>()

    private fun renderResult(value: Any?, replId: Int): Any? {
        if (value == null) return null
        val code = typeRenderers[value.javaClass.canonicalName]?.replace("\$it", "res$replId")
                ?: return value
        val result = doEval(code)
        return renderResult(result.value, result.replId)
    }

    data class PreprocessingResult(val code: Code, val initCodes: List<Code>, val initCellCodes: List<Code>, val typeRenderers: List<TypeHandler>)

    fun preprocessCode(code: String): PreprocessingResult {

        val processedMagics = magics.processMagics(code)

        val initCodes = mutableListOf<Code>()
        val initCellCodes = mutableListOf<Code>()
        val typeRenderers = mutableListOf<TypeHandler>()
        val typeConverters = mutableListOf<TypeHandler>()
        val annotations = mutableListOf<TypeHandler>()

        processedMagics.libraries.forEach {
            val builder = StringBuilder()
            it.repositories.forEach { builder.appendln("@file:Repository(\"$it\")") }
            it.dependencies.forEach { builder.appendln("@file:DependsOn(\"$it\")") }
            it.imports.forEach { builder.appendln("import $it") }
            if (builder.isNotBlank())
                initCodes.add(builder.toString())
            typeRenderers.addAll(it.renderers)
            typeConverters.addAll(it.converters)
            annotations.addAll(it.annotations)
            it.init.forEach {

                // Library init code may contain other magics, so we process them recursively
                val preprocessed = preprocessCode(it)
                initCodes.addAll(preprocessed.initCodes)
                typeRenderers.addAll(preprocessed.typeRenderers)
                initCellCodes.addAll(preprocessed.initCellCodes)
                if (preprocessed.code.isNotBlank())
                    initCodes.add(preprocessed.code)
            }
        }

        val declarations = (typeConverters.map { typeProvidersProcessor.register(it) } + annotations.map { annotationsProcessor.register(it) })
                .joinToString("\n")
        if (declarations.isNotBlank()) {
            initCodes.add(declarations)
        }

        return PreprocessingResult(processedMagics.code, initCodes, initCellCodes, typeRenderers)
    }

    private val ctx = KotlinContext()

    private val receivers: List<Any> = listOf<Any>(KotlinReceiver(ctx)) + scriptReceivers

    val magics = MagicsProcessor(this, LibrariesProcessor(resolverConfig?.libraries))

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
            defaultImports(DependsOn::class, Repository::class, ScriptTemplateWithDisplayHelpers::class, KotlinReceiver::class)
            jvm {
                updateClasspath(scriptClasspath)
            }
            refineConfiguration {
                onAnnotations(DependsOn::class, Repository::class, handler = { configureMavenDepsOnAnnotations(it) })
            }

            implicitReceivers.invoke(receivers.map { KotlinType(it.javaClass.canonicalName) })

            compilerOptions.invoke(listOf("-jvm-target", "1.8"))
        }
    }

    val ScriptCompilationConfiguration.classpath
        get() = this[ScriptCompilationConfiguration.dependencies]
                ?.filterIsInstance<JvmDependency>()
                ?.flatMap { it.classpath }
                .orEmpty()

    override val currentClasspath = mutableSetOf<String>().also { it.addAll(compilerConfiguration.classpath.map { it.canonicalPath }) }

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
        implicitReceivers.invoke(v = receivers)
        jvm {
            val filteringClassLoader = FilteringClassLoader(ClassLoader.getSystemClassLoader()) {
                it.startsWith("jupyter.kotlin.") || it.startsWith("kotlin.") || (it.startsWith("org.jetbrains.kotlin.") && !it.startsWith("org.jetbrains.kotlin.jupyter."))
            }
            val scriptClassloader = URLClassLoader(scriptClasspath.map { it.toURI().toURL() }.toTypedArray(), filteringClassLoader)
            baseClassLoader(scriptClassloader)
        }
        constructorArgs()
    }

    private var executionCounter = 0

    private val compiler: ReplCompilerWithCompletion by lazy {
        JvmReplCompiler(compilerConfiguration)
    }

    private val evaluator: ReplEvaluator by lazy {
        JvmReplEvaluator(evaluatorConfiguration)
    }

    private val stateLock = ReentrantReadWriteLock()

    private val compilerState = compiler.createState(stateLock)

    private val evaluatorState = evaluator.createState(stateLock)

    private val state = AggregatedReplStageState(compilerState, evaluatorState, stateLock)

    private val completer = KotlinCompleter()

    private val contextUpdater = ContextUpdater(state, ctx)

    private val typeProvidersProcessor: TypeProvidersProcessor = TypeProvidersProcessorImpl(contextUpdater)

    private val annotationsProcessor: AnnotationsProcessor = AnnotationsProcessorImpl(contextUpdater)

    private var currentDisplayHandler: ((Any) -> Unit)? = null

    private val scheduledExecutions = LinkedList<Code>()

    override fun checkComplete(executionNumber: Long, code: String): CheckResult {
        val codeLine = ReplCodeLine(executionNumber.toInt(), 0, code)
        return when (val result = compiler.check(compilerState, codeLine)) {
            is ReplCheckResult.Error -> throw ReplCompilerException(result)
            is ReplCheckResult.Ok -> CheckResult(LineId(codeLine), true)
            is ReplCheckResult.Incomplete -> CheckResult(LineId(codeLine), false)
            else -> throw IllegalStateException("Unknown check result type ${result}")
        }
    }

    init {
        // TODO: to be removed after investigation of https://github.com/kotlin/kotlin-jupyter/issues/24
        doEval("1")
    }

    private fun executeInitCellCode() = initCellCodes.forEach(::evalNoReturn)

    private fun executeInitCode(p: PreprocessingResult) = p.initCodes.forEach(::evalNoReturn)

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

    fun registerNewLibraries(p: PreprocessingResult) {
        p.initCellCodes.filter { !initCellCodes.contains(it) }.let(initCellCodes::addAll)
        typeRenderers.putAll(p.typeRenderers.map { it.className to it.code })
    }

    private fun lastReplLine() = state.lines[0]

    override fun eval(code: String, displayHandler: ((Any) -> Unit)?, jupyterId: Int): EvalResult {
        synchronized(this) {
            try {

                currentDisplayHandler = displayHandler

                executeInitCellCode()

                val preprocessed = preprocessCode(code)

                executeInitCode(preprocessed)

                var result: Any? = null
                var replId = -1
                var replLine: Any? = null

                if (preprocessed.code.isNotBlank()) {
                    doEval(preprocessed.code).let {
                        result = it.value
                        replId = it.replId
                    }
                    replLine = lastReplLine()
                }

                processAnnotations(replLine)

                executeScheduledCode()

                registerNewLibraries(preprocessed)

                processVariablesConversion()

                executeScheduledCode()

                updateOutputList(jupyterId, result)

                updateClasspath()

                result = renderResult(result, replId)

                return EvalResult(result)

            } finally {
                currentDisplayHandler = null
                scheduledExecutions.clear()
            }
        }
    }

    private val completionQueue = CompletionLockQueue()

    override fun complete(code: String, cursor: Int): CompletionResult {
        val args = CompletionArgs(code, cursor)
        completionQueue.add(args)

        synchronized(this) {
            val lastArgs = completionQueue.get()
            if (lastArgs != args)
                return CompletionResultEmpty(code, cursor)

            val id = executionCounter++
            val codeLine = ReplCodeLine(id, 0, code)
            return completer.complete(compiler, compilerState, codeLine, cursor)
        }
    }

    private fun updateOutputList(jupyterId: Int, result: Any?) {
        if (jupyterId >= 0) {
            while (ReplOutputs.count() <= jupyterId) ReplOutputs.add(null)
            ReplOutputs[jupyterId] = result
        }
    }

    private fun updateClasspath() {
        val resolvedClasspath = resolver.popAddedClasspath().map { it.canonicalPath }
        if (resolvedClasspath.isNotEmpty()) {

            val newClasspath = resolvedClasspath.filter { !currentClasspath.contains(it) }
            val oldClasspath = resolvedClasspath.filter { currentClasspath.contains(it) }
            currentClasspath.addAll(newClasspath)
            if (trackClasspath) {
                val sb = StringBuilder()
                if (newClasspath.count() > 0) {
                    sb.appendln("${newClasspath.count()} new paths were added to classpath:")
                    newClasspath.sortedBy { it }.forEach { sb.appendln(it) }
                }
                if (oldClasspath.count() > 0) {
                    sb.appendln("${oldClasspath.count()} resolved paths were already in classpath:")
                    oldClasspath.sortedBy { it }.forEach { sb.appendln(it) }
                }
                sb.appendln("Current classpath size: ${currentClasspath.count()}")
                println(sb.toString())
            }
        }
    }

    private data class InternalEvalResult(val value: Any?, val replId: Int)

    private data class CompletionArgs(val code: String, val cursor: Int)

    private class CompletionLockQueue {
        private var args: CompletionArgs? = null

        fun add(args: CompletionArgs) {
            synchronized(this) {
                this.args = args
            }
        }

        fun get(): CompletionArgs {
            return args!!
        }
    }

    private fun evalNoReturn(code: String) {
        doEval(code)
        processAnnotations(lastReplLine())
    }

    private fun doEval(code: String): InternalEvalResult {
        if (executedCodeLogging == ExecutedCodeLogging.All)
            println(code)
        val id = executionCounter++
        val codeLine = ReplCodeLine(id, 0, code)
        when (val compileResult = compiler.compile(compilerState, codeLine)) {
            is ReplCompileResult.CompiledClasses -> {
                classWriter?.writeClasses(compileResult)
                val scriptArgs = ScriptArgsWithTypes(arrayOf(this), arrayOf(KotlinKernelHost::class))
                val result = evaluator.eval(evaluatorState, compileResult, scriptArgs)
                contextUpdater.update()
                return when (result) {
                    is ReplEvalResult.Error.CompileTime -> throw ReplCompilerException(result)
                    is ReplEvalResult.Error.Runtime -> throw ReplEvalRuntimeException(result)
                    is ReplEvalResult.Incomplete -> throw ReplCompilerException(result)
                    is ReplEvalResult.HistoryMismatch -> throw ReplCompilerException(result)
                    is ReplEvalResult.UnitResult -> {
                        InternalEvalResult(Unit, id)
                    }
                    is ReplEvalResult.ValueResult -> {
                        InternalEvalResult(result.value, id)
                    }
                    else -> throw IllegalStateException("Unknown eval result type ${this}")
                }
            }
            is ReplCompileResult.Error -> throw ReplCompilerException(compileResult)
            is ReplCompileResult.Incomplete -> throw ReplCompilerException(compileResult)
        }
    }

    init {
        log.info("Starting kotlin REPL engine. Compiler version: ${KotlinCompilerVersion.VERSION}")
        log.info("Classpath used in script: ${scriptClasspath}")
    }

    override fun display(value: Any) {
        currentDisplayHandler?.invoke(value)
    }

    override fun scheduleExecution(code: String) {
        scheduledExecutions.add(code)
    }
}

