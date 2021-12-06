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
import org.jetbrains.kotlinx.jupyter.repl.InternalVariablesMarkersProcessor
import java.lang.reflect.Field
import java.lang.reflect.Modifier
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

    override fun getVariablesDeclarationInfo(): Map<String, Int> = variablesWatcher.variablesDeclarationInfo

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

        val fields = kClass.java.declaredFields
        // ignore implementation details of top level like script instance and result value
        val kProperties = kClass.declaredMemberProperties.associateBy { it.name }

        return mutableMapOf<String, VariableStateImpl>().apply {
            val addedDeclarations = mutableSetOf<String>()
            for (property in fields) {
                val state = VariableStateImpl(property, cellClassInstance)

                val isInternalKProperty = kProperties[property.name]?.let {
                    @Suppress("UNCHECKED_CAST")
                    it as KProperty1<Any, *>
                    internalVariablesMarkersProcessor.isInternal(it)
                }

                if (isInternalKProperty == true || !kProperties.contains(property.name)) continue

                variablesWatcher.addDeclaration(cellId, property.name)
                addedDeclarations.add(property.name)

                // it was val, now it's var
                if (isValField(property)) {
                    variablesHolder.remove(property.name)
                } else {
                    variablesHolder[property.name] = state
                    continue
                }

                put(property.name, state)
            }
            // remove old
            variablesWatcher.removeOldDeclarations(cellId, addedDeclarations)
        }
    }

    private fun isValField(property: Field): Boolean {
        return property.modifiers and Modifier.FINAL != 0
    }

    private fun updateDataAfterExecution(lastExecutionCellId: Int, resultValue: ResultValue) {
        variablesWatcher.ensureStorageCreation(lastExecutionCellId)
        variablesHolder += getVisibleVariables(resultValue, lastExecutionCellId)
        // remove unreached variables
        updateVariablesState(lastExecutionCellId)
    }
}
