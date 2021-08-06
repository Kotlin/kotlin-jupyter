package org.jetbrains.kotlinx.jupyter.repl.impl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.VariablesUsagesPerCellWatcher
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.api.VariableStateImpl
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScript
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScriptsData
import org.jetbrains.kotlinx.jupyter.compiler.util.SourceCodeImpl
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater
import org.jetbrains.kotlinx.jupyter.repl.InternalEvalResult
import org.jetbrains.kotlinx.jupyter.repl.InternalEvaluator
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import org.jetbrains.kotlinx.jupyter.repl.InternalVariablesMarkersProcessor
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
    private val internalVariablesMarkersProcessor: InternalVariablesMarkersProcessor,
) :
    InternalEvaluator {

    private var classWriter: ClassWriter? = null

    private val scriptsSerializer = CompiledScriptsSerializer()

    private val registeredCompiledScripts = arrayListOf<SerializedCompiledScript>()

    private fun serializeAndRegisterScript(compiledScript: KJvmCompiledScript) {
        val serializedData = scriptsSerializer.serialize(compiledScript)
        registeredCompiledScripts.addAll(serializedData.scripts)
    }

    override fun popAddedCompiledScripts(): SerializedCompiledScriptsData {
        val scripts = registeredCompiledScripts.toList()
        registeredCompiledScripts.clear()
        return SerializedCompiledScriptsData(scripts)
    }

    override fun findVariableCell(variableName: String): Int {
        return variablesWatcher.findDeclarationAddress(variableName) ?: -1
    }

    override fun getUnchangedVariables(): Set<String> {
        return variablesWatcher.getUnchangedVariables()
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

    override val cellVariables: Map<Int, Set<String>>
        get() = variablesWatcher.cellVariables

    private val variablesWatcher: VariablesUsagesPerCellWatcher<Int, String> = VariablesUsagesPerCellWatcher()

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
        variablesWatcher.removeOldUsages(cellId)

        variablesHolder.forEach {
            val state = it.value as VariableStateImpl

            if (state.update()) {
                variablesWatcher.addUsage(cellId, it.key)
            }
        }
    }

    private fun getVisibleVariables(target: ResultValue, cellId: Int): Map<String, VariableStateImpl> {
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

    private fun isValField(property: Field): Boolean {
        return property.modifiers and Modifier.FINAL != 0
    }

    private fun updateDataAfterExecution(lastExecutionCellId: Int, resultValue: ResultValue) {
        variablesWatcher.ensureStorageCreation(lastExecutionCellId)
        variablesHolder += getVisibleVariables(resultValue, lastExecutionCellId)

        updateVariablesState(lastExecutionCellId)
    }
}
