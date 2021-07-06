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
     * stores cache for val values
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

    override val variablesMap = mutableMapOf<String, VariableState>()

    private var isExecuting = false

    override fun eval(code: Code, onInternalIdGenerated: ((Int) -> Unit)?): InternalEvalResult {
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

                            variablesMap += getVisibleVariables(resultValue)
                            updateVariablesState()

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

    private fun updateVariablesState() {
        variablesMap.forEach {
            val state = it.value
            val property = state.propertyKlass
            val newValue = property.get(state.scriptInstance) ?: return@forEach

            if (state.stringValue != newValue) {
                state.stringValue = newValue.toString()
            }
        }
    }


    private fun getVisibleVariables(target: ResultValue): Map<String, VariableState> {
        val klass = target.scriptClass ?: return emptyMap()
        val cellKlassInstance = target.scriptInstance!!

        val fields = klass.declaredMemberProperties
        val ans = mutableMapOf<String, VariableState>()
        fields.forEach {
            val casted = it as KProperty1<Any, *>
            ans[casted.name] = if (casted.get(cellKlassInstance) == null) {
                return@forEach
            } else {
                val stringVal = casted.get(cellKlassInstance).toString()
                val state = VariableState(casted, cellKlassInstance, stringVal)
                // invariant with changes: cache --> varsMap, other way is not allowed
                if (it !is KMutableProperty1) {
                    variablesCache[casted.name] = state
                    variablesMap[casted.name] = state
                    return@forEach
                }
                state
            }
        }
        return ans
    }

    // this is the O(n) implementation
    @Deprecated("Back-propagation def search", ReplaceWith("updateVariablesState"))
    private fun traverseSeenVarsNaive() {
        var lastSnipped = evaluator.lastEvaluatedSnippet?.previous ?: return
        val seenVars = mutableSetOf<String>()
        do {
            val target = lastSnipped.get().result
//            updateVariablesMap(target, seenVars)
            lastSnipped = lastSnipped.previous ?: break
        } while (true)
    }

}
