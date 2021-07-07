package org.jetbrains.kotlinx.jupyter.repl.impl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScript
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScriptsData
import org.jetbrains.kotlinx.jupyter.compiler.util.SourceCodeImpl
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater
import org.jetbrains.kotlinx.jupyter.repl.InternalEvalResult
import org.jetbrains.kotlinx.jupyter.repl.InternalEvaluator
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.jvm.BasicJvmReplEvaluator
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript

internal class InternalEvaluatorImpl(
    val compiler: JupyterCompiler,
    private val evaluator: BasicJvmReplEvaluator,
    private val contextUpdater: ContextUpdater,
    override var logExecution: Boolean,
) :
    InternalEvaluator {

    private var classWriter: ClassWriter? = null

    private val scriptsSerializer = CompiledScriptsSerializer()

    private val registeredCompiledScripts = arrayListOf<SerializedCompiledScript>()

    /**
     * Stores cache for val values.
     * Its contents is a part of variablesHolder.
     */
    private val variablesCache = mutableMapOf<String, VariableState>()

    private fun serializeAndRegisterScript(compiledScript: KJvmCompiledScript) {
        val serializedData = scriptsSerializer.serialize(compiledScript)
        registeredCompiledScripts.addAll(serializedData.scripts)
    }

    override fun popAddedCompiledScripts(): SerializedCompiledScriptsData {
        val scripts = registeredCompiledScripts.toList()
        registeredCompiledScripts.clear()
        return SerializedCompiledScriptsData(scripts)
    }

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

    override val lastKClass get() = compiler.lastKClass

    override val lastClassLoader get() = compiler.lastClassLoader

    override val variablesHolder = mutableMapOf<String, VariableState>()

    override val varsUsagePerCell = mutableMapOf<Int, MutableSet<String>>()

    /**
     * Tells in which cell a variable was declared
     */
    private val varsDeclarationInfo: MutableMap<String, Int> = mutableMapOf()

    private var isExecuting = false

    override fun eval(code: Code, cellId: Int, onInternalIdGenerated: ((Int) -> Unit)?): InternalEvalResult {
        try {
            if (isExecuting) {
                error("Recursive execution is not supported")
            }

            isExecuting = true
            if (logExecution) {
                println("Executing:\n$code")
            }
            val id = compiler.nextCounter()

            if (onInternalIdGenerated != null) {
                onInternalIdGenerated(id)
            }

            val codeLine = SourceCodeImpl(id, code)

            val (compileResult, evalConfig) = compiler.compileSync(codeLine)
            val compiledScript = compileResult.get()

            classWriter?.writeClasses(codeLine, compiledScript)
            val resultWithDiagnostics = runBlocking { evaluator.eval(compileResult, evalConfig) }
            contextUpdater.update()

            when (resultWithDiagnostics) {
                is ResultWithDiagnostics.Success -> {
                    val pureResult = resultWithDiagnostics.value.get()
                    return when (val resultValue = pureResult.result) {
                        is ResultValue.Error -> throw ReplEvalRuntimeException(
                            resultValue.error.message.orEmpty(),
                            resultValue.error
                        )
                        is ResultValue.Unit, is ResultValue.Value -> {
                            serializeAndRegisterScript(compiledScript)
                            updateDataAfterExecution(cellId, resultValue)

                            if (resultValue is ResultValue.Unit) {
                                InternalEvalResult(
                                    FieldValue(Unit, null),
                                    resultValue.scriptInstance!!
                                )
                            } else {
                                resultValue as ResultValue.Value
                                InternalEvalResult(
                                    FieldValue(resultValue.value, resultValue.name),
                                    resultValue.scriptInstance!!
                                )
                            }
                        }
                        is ResultValue.NotEvaluated -> {
                            throw ReplEvalRuntimeException(
                                "This snippet was not evaluated",
                                resultWithDiagnostics.reports.firstOrNull()?.exception
                            )
                        }
                        else -> throw IllegalStateException("Unknown eval result type $this")
                    }
                }
                is ResultWithDiagnostics.Failure -> {
                    throw ReplCompilerException(code, resultWithDiagnostics)
                }
                else -> throw IllegalStateException("Unknown result")
            }
        } finally {
            isExecuting = false
        }
    }

    private fun updateVariablesState(cellId: Int) {
        // remove known modifying usages in this cell
        varsUsagePerCell[cellId]?.removeIf {
            varsDeclarationInfo[it] != cellId
        }

        variablesHolder.forEach {
            val state = it.value
            val oldValue = state.stringValue
            state.update()

            if (state.stringValue != oldValue) {
                varsUsagePerCell[cellId]?.add(it.key)
            }
        }
    }

    private fun getVisibleVariables(target: ResultValue, cellId: Int): Map<String, VariableState> {
        val kClass = target.scriptClass ?: return emptyMap()
        val cellKlassInstance = target.scriptInstance!!

        val fields = kClass.declaredMemberProperties
        val ans = mutableMapOf<String, VariableState>()
        fields.forEach { property ->
            property as KProperty1<Any, *>
            val state = VariableState(property, cellKlassInstance)

            // redeclaration of any type
            if (varsDeclarationInfo.containsKey(property.name)) {
                val oldCellId = varsDeclarationInfo[property.name]
                if (oldCellId != cellId) {
                    varsUsagePerCell[oldCellId]?.remove(property.name)
                }
            }
            // it was val, now it's var
            if (property is KMutableProperty1) {
                variablesCache.remove(property.name)
            }
            varsDeclarationInfo[property.name] = cellId
            varsUsagePerCell[cellId]?.add(property.name)
            // invariant with changes: cache --> varsMap, other way is not allowed
            if (property !is KMutableProperty1) {
                variablesCache[property.name] = state
                variablesHolder[property.name] = state
                return@forEach
            }
            ans[property.name] = state
        }
        return ans
    }

    private fun updateDataAfterExecution(lastExecutionCellId: Int, resultValue: ResultValue) {
        varsUsagePerCell.putIfAbsent(lastExecutionCellId, mutableSetOf())

        val visibleDeclarations = getVisibleVariables(resultValue, lastExecutionCellId)
        visibleDeclarations.forEach {
            val cellSet = varsUsagePerCell[lastExecutionCellId] ?: return@forEach
            varsDeclarationInfo[it.key] = lastExecutionCellId
            cellSet += it.key
        }
        variablesHolder += visibleDeclarations

        updateVariablesState(lastExecutionCellId)
    }
}
