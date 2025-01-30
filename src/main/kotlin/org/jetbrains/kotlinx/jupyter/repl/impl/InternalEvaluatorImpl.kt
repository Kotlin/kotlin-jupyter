package org.jetbrains.kotlinx.jupyter.repl.impl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.K2ReplEvaluator
import org.jetbrains.kotlinx.jupyter.VariablesUsagesPerCellWatcher
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.KTypeProvider
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.api.VariableStateImpl
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.compiler.util.SourceCodeImpl
import org.jetbrains.kotlinx.jupyter.config.JupyterCompilingOptions
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.repl.CellErrorMetaData
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater
import org.jetbrains.kotlinx.jupyter.repl.ExecutedCodeLogging
import org.jetbrains.kotlinx.jupyter.repl.InternalEvaluator
import org.jetbrains.kotlinx.jupyter.repl.InternalVariablesMarkersProcessor
import org.jetbrains.kotlinx.jupyter.repl.execution.EvaluatorWorkflowListener
import org.jetbrains.kotlinx.jupyter.repl.result.InternalEvalResult
import org.jetbrains.kotlinx.jupyter.repl.result.SerializedCompiledScriptsData
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.typeOf
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript

internal class InternalEvaluatorImpl(
    private val repl: ReplForJupyterImpl,
    private val loggerFactory: KernelLoggerFactory,
    val compiler: JupyterCompiler,
    private val evaluator: K2ReplEvaluator,
    private val contextUpdater: ContextUpdater,
    private val internalVariablesMarkersProcessor: InternalVariablesMarkersProcessor,
    executionLoggingProperty: KMutableProperty0<ExecutedCodeLogging>,
    serializeScriptDataProperty: KMutableProperty0<Boolean>,
    writeCompiledClassesProperty: KMutableProperty0<Boolean>,
) : InternalEvaluator {
    private var classWriter: ClassWriter? = null

    private val scriptsSerializer = CompiledScriptsSerializer()

    private val registeredCompiledScripts = SerializedCompiledScriptsData.Builder()

    // Track the mapping between the fully qualified name of the compiled script to the
    // Jupyter execution request counter. This can be used to enhance a stacktrace so we
    // can map each line of the stacktrace to the relevant code cell and line number.
    private val scriptFqnToExecutionCountTracker = mutableMapOf<String, CellErrorMetaData>()

    private fun serializeAndRegisterScript(
        compiledScript: KJvmCompiledScript,
        source: SourceCode,
    ) {
        if (!serializeScriptData) return
        val serializedData = scriptsSerializer.serialize(compiledScript, source)
        registeredCompiledScripts.addData(serializedData)
    }

    override fun popAddedCompiledScripts(): SerializedCompiledScriptsData {
        val data = registeredCompiledScripts.build()
        registeredCompiledScripts.clear()
        return data
    }

    override var writeCompiledClasses: Boolean by writeCompiledClassesProperty

    private fun withClassWriter(block: ClassWriter.() -> Unit) {
        if (!writeCompiledClasses) {
            classWriter = null
            return
        }
        val myClassWriter =
            classWriter ?: run {
                val cw = ClassWriter(loggerFactory)
                System.setProperty("spark.repl.class.outputDir", cw.outputDir.toString())
                cw
            }
        myClassWriter.apply(block)
    }

    override var executionLogging: ExecutedCodeLogging by executionLoggingProperty

    override var serializeScriptData: Boolean by serializeScriptDataProperty

    override val lastKClass get() = compiler.lastKClass

    override val lastClassLoader get() = compiler.lastClassLoader

    override val variablesHolder = mutableMapOf<String, VariableState>()

    override val cellVariables: Map<Int, Set<String>>
        get() = variablesWatcher.cellVariables

    private val variablesWatcher: VariablesUsagesPerCellWatcher<Int, String> = VariablesUsagesPerCellWatcher()

    private var isExecuting = false

    override fun eval(
        code: Code,
        compilingOptions: JupyterCompilingOptions,
        evaluatorWorkflowListener: EvaluatorWorkflowListener?,
    ): InternalEvalResult {
        try {
            if (isExecuting) {
                error("Recursive execution is not supported")
            }

            // HACK Begin ---
            // We attempt to wrap user code in a custom receiver to work around capturing
            // not working in the compiler just yet. Right now we use the heuristic that
            // we assume line magics and annotations are always first, and everything below
            // that is kotlin code.
            val mutCode = code.lines().toMutableList()
            val metaCodeEndsAt = mutCode.indexOfLast {
                it.startsWith("@file:") || it.startsWith("%") || it.startsWith("import ")
            }
            val modifiedCode = buildString {
                mutCode.subList(0, metaCodeEndsAt + 1).forEach {
                    appendLine(it)
                }
//                appendLine("with (notebookReceiver) {")
                mutCode.subList(metaCodeEndsAt + 1, mutCode.size).forEach {
                    appendLine(it)
                }
//                appendLine("}")
            }
            loggerFactory.getLogger(this::class.java).debug("Wrapped code:\n$modifiedCode")
            // --- End HACK

//            // HACK Begin ---
//            // Work-around for https://youtrack.jetbrains.com/projects/KT/issues/KT-74593/K2-Repl-defaultImports-does-not-work-in-ScriptCompilationConfiguration
//            val defaultImports = repl.compilerConfiguration.get<List<String>>(Key("defaultImports"))?.map {
//                "import $it"
//            } ?: emptyList()
//            code.replace("@file:DependsOn", "@file:kotlin.jupyter.DependsOn")
//            val mutCode = code.lines().toMutableList()
//            val insertAt = mutCode.indexOfLast { it.startsWith("@file:") }
//            defaultImports.forEachIndexed { i, el ->
//                mutCode.add(insertAt + i + 1, el)
//            }
//            val codeWithDefaultImports = mutCode.joinToString("\n")
//            // --- End HACK

            isExecuting = true
            if (executionLogging == ExecutedCodeLogging.ALL) {
                println("Executing:\n$modifiedCode")
            }
            val id = compiler.nextCounter()

            evaluatorWorkflowListener?.internalIdGenerated(id)

            val codeLine = SourceCodeImpl(id, modifiedCode)

            val (compileResult, evalConfig) = compiler.compileSync(codeLine, compilingOptions)
            evaluatorWorkflowListener?.compilationFinished()
            val compiledScript = compileResult.get()
            scriptFqnToExecutionCountTracker[compiledScript.scriptClassFQName] =
                CellErrorMetaData(
                    compilingOptions.cellId.toExecutionCount(),
                    modifiedCode.lines().size,
                )
            withClassWriter {
                writeClasses(codeLine, compiledScript)
            }
            val resultWithDiagnostics = runBlocking { evaluator.eval(compileResult, evalConfig) }
            contextUpdater.update()

            when (resultWithDiagnostics) {
                is ResultWithDiagnostics.Success -> {
                    val pureResult = resultWithDiagnostics.value.get()
                    return when (val resultValue = pureResult.result) {
                        is ResultValue.Error -> throw ReplEvalRuntimeException(
                            repl.fileExtension,
                            scriptFqnToExecutionCountTracker,
                            resultValue.error.message.orEmpty(),
                            resultValue.error,
                        )
                        is ResultValue.Unit, is ResultValue.Value -> {
                            serializeAndRegisterScript(compiledScript, codeLine)
                            updateDataAfterExecution(compilingOptions.cellId.value, resultValue)

                            val resultType = compiledScript.resultField?.second?.typeName
                            val typeProvider =
                                if (resultType == null) {
                                    KTypeProvider { typeOf<Any?>() }
                                } else {
                                    KTypeProvider { eval("kotlin.reflect.typeOf<$resultType>()").result.value as KType }
                                }

                            if (resultValue is ResultValue.Unit) {
                                InternalEvalResult(
                                    FieldValue(Unit, null, typeProvider),
                                    resultValue.scriptInstance!!,
                                )
                            } else {
                                resultValue as ResultValue.Value
                                InternalEvalResult(
                                    FieldValue(resultValue.value, resultValue.name, typeProvider),
                                    resultValue.scriptInstance!!,
                                )
                            }
                        }
                        is ResultValue.NotEvaluated -> {
                            throw ReplEvalRuntimeException(
                                repl.fileExtension,
                                scriptFqnToExecutionCountTracker,
                                "This snippet was not evaluated",
                                resultWithDiagnostics.reports.firstOrNull()?.exception,
                            )
                        }
                        else -> throw IllegalStateException("Unknown eval result type $this")
                    }
                }
                is ResultWithDiagnostics.Failure -> {
                    val metadata =
                        CellErrorMetaData(
                            compilingOptions.cellId.toExecutionCount(),
                            modifiedCode.lines().size,
                        )
                    throw ReplCompilerException(modifiedCode, resultWithDiagnostics, metadata = metadata)
                }
                else -> throw IllegalStateException("Unknown result")
            }
        } finally {
            isExecuting = false
        }
    }

    private fun updateVariablesState(cellId: Int) {
        variablesWatcher.removeOldUsages(cellId)

        variablesHolder.forEach {
            val state = it.value as VariableStateImpl

            if (state.update()) {
                variablesWatcher.addUsage(cellId, it.key)
            }
        }
    }

    private fun getVisibleVariables(
        target: ResultValue,
        cellId: Int,
    ): Map<String, VariableStateImpl> {
        val kClass = target.scriptClass ?: return emptyMap()
        val cellClassInstance = target.scriptInstance!!

        val fields = kClass.declaredMemberProperties
        return mutableMapOf<String, VariableStateImpl>().apply {
            for (property in fields) {
                @Suppress("UNCHECKED_CAST")
                property as KProperty1<Any, *>
                if (internalVariablesMarkersProcessor.isInternal(property)) continue

                val state = VariableStateImpl(property, cellClassInstance)
                variablesWatcher.addDeclaration(cellId, property.name)

                // it was val, now it's var
                if (property is KMutableProperty1) {
                    variablesHolder.remove(property.name)
                } else {
                    variablesHolder[property.name] = state
                    continue
                }

                put(property.name, state)
            }
        }
    }

    private fun updateDataAfterExecution(
        lastExecutionCellId: Int,
        resultValue: ResultValue,
    ) {
        variablesWatcher.ensureStorageCreation(lastExecutionCellId)
        variablesHolder += getVisibleVariables(resultValue, lastExecutionCellId)

        updateVariablesState(lastExecutionCellId)
    }
}
