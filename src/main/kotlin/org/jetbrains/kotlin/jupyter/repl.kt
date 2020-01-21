package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.*
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.jupyter.repl.completion.CompletionResult
import org.jetbrains.kotlin.jupyter.repl.completion.KotlinCompleter
import org.jetbrains.kotlin.jupyter.repl.reflect.ContextUpdater
import org.jetbrains.kotlin.jupyter.repl.spark.ClassWriter
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.withDefaultsFrom
import kotlin.script.experimental.jvm.*
import kotlin.script.experimental.jvmhost.repl.JvmReplCompiler
import kotlin.script.experimental.jvmhost.repl.JvmReplEvaluator

data class EvalResult(val resultValue: Any?, val displayValues: List<Any> = emptyList())

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

class ReplForJupyter(val scriptClasspath: List<File> = emptyList(),
                     val config: ResolverConfig? = null) {

    val outputConfig = OutputConfig()

    private val resolver = JupyterScriptDependenciesResolver(config)

    private val renderers = config?.let {
        it.libraries.asyncLet {
            it.flatMap { it.value.renderers }.map { it.className to it }.toMap()
        }
    }

    private val includedLibraries = mutableSetOf<LibraryDefinition>()

    fun preprocessCode(code: String) = processMagics(this, code)

    private val receiver = KotlinReceiver()

    val librariesCodeGenerator = LibrariesProcessor()

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

            val kt = KotlinType(receiver.javaClass.canonicalName)
            implicitReceivers.invoke(listOf(kt))

            log.info("Classpath for compiler options: none")
            compilerOptions.invoke(listOf("-jvm-target", "1.8"))
        }
    }

    val ScriptCompilationConfiguration.classpath
        get() = this[ScriptCompilationConfiguration.dependencies]
                ?.filterIsInstance<JvmDependency>()
                ?.flatMap { it.classpath }
                .orEmpty()

    val currentClasspath = mutableSetOf<String>().also { it.addAll(compilerConfiguration.classpath.map { it.canonicalPath }) }

    private class FilteringClassLoader(parent: ClassLoader, val includeFilter: (String) -> Boolean) : ClassLoader(parent) {
        override fun loadClass(name: String?, resolve: Boolean): Class<*> {
            var c: Class<*>? = null
            c = if (name != null && includeFilter(name))
                parent.loadClass(name)
            else parent.parent.loadClass(name)
            if (resolve)
                resolveClass(c)
            return c
        }
    }

    private val evaluatorConfiguration = ScriptEvaluationConfiguration {
        implicitReceivers.invoke(receiver)
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

    private val ctx = KotlinContext()

    private val contextUpdater = ContextUpdater(state, ctx.vars, ctx.functions)

    var trackClasspath: Boolean = false

    var trackExecutedCode: Boolean = false

    var classWriter: ClassWriter? = null

    fun checkComplete(executionNumber: Long, code: String): CheckResult {
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

    fun eval(code: String, jupyterId: Int = -1): EvalResult {
        synchronized(this) {
            try {
                val displays = mutableListOf<Any>()

                val initCell = includedLibraries.flatMap { it.initCell }.joinToString(separator = "\n")

                if (initCell.isNotBlank())
                    (doEval(initCell).value as? DisplayResult)?.let(displays::add)

                val processedCode = preprocessCode(code)

                val newLibraries = librariesCodeGenerator.getProcessedLibraries()

                newLibraries.forEach {
                    (doEval(it.code).value as? DisplayResult)?.let(displays::add)
                }

                var result: Any? = null
                var replId = -1

                if (processedCode.isNotBlank()) {
                    val e = doEval(processedCode)
                    result = e.value
                    replId = e.replId
                }

                // on successful execution add all new libraries to the set
                includedLibraries.addAll(newLibraries.map { it.library })

                if (jupyterId >= 0) {
                    while (ReplOutputs.count() <= jupyterId) ReplOutputs.add(null)
                    ReplOutputs[jupyterId] = result
                }

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
                        displays.add(sb.toString())
                    }
                }

                if (result != null && renderers != null) {
                    val resultType = result.javaClass.canonicalName
                    renderers.awaitBlocking()[resultType]?.let {
                        it.displayCode?.let {
                            doEval(it.replace("\$it", "res$replId")).value?.let(displays::add)
                        }
                        result = if (it.resultCode == null || it.resultCode.trim().isBlank()) ""
                        else doEval(it.resultCode.replace("\$it", "res$replId")).value
                    }
                    if (result is DisplayResult) {
                        displays.add(result as Any)
                        result = ""
                    }
                }
                return EvalResult(result, displays)
            } finally {
                librariesCodeGenerator.getProcessedLibraries()
            }
        }
    }

    fun complete(code: String, cursor: Int): CompletionResult {
        val id = executionCounter++
        val codeLine = ReplCodeLine(id, 0, code)
        return completer.complete(compiler, compilerState, codeLine, cursor)
    }

    private data class InternalEvalResult(val value: Any?, val replId: Int)

    private fun doEval(code: String): InternalEvalResult {
        if (trackExecutedCode)
            println("Executing:\n$code\n")
        val id = executionCounter++
        val codeLine = ReplCodeLine(id, 0, code)
        when (val compileResult = compiler.compile(compilerState, codeLine)) {
            is ReplCompileResult.CompiledClasses -> {
                classWriter?.writeClasses(compileResult)
                val result = evaluator.eval(evaluatorState, compileResult)
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
        receiver.kc = ctx
    }
}

