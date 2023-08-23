package org.jetbrains.kotlinx.jupyter.test.repl

import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.FieldValue
import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.config.JupyterCompilingOptions
import org.jetbrains.kotlinx.jupyter.repl.CellExecutor
import org.jetbrains.kotlinx.jupyter.repl.InternalEvalResult
import org.jetbrains.kotlinx.jupyter.repl.InternalEvaluator
import org.jetbrains.kotlinx.jupyter.repl.impl.CellExecutorImpl
import org.jetbrains.kotlinx.jupyter.repl.workflow.EvaluatorWorkflowListener
import kotlin.reflect.KClass

interface TrackedCellExecutor : CellExecutor {

    val executedCodes: List<Code>

    val results: List<Any?>

    companion object {

        fun create(baseRepl: ReplForJupyter, mockEvaluator: Boolean): TrackedCellExecutor {
            baseRepl as ReplForJupyterImpl
            val context = baseRepl.sharedContext
            val evaluator = if (mockEvaluator) MockedInternalEvaluator() else TrackedInternalEvaluatorImpl(context.evaluator)
            val hackedContext = context.copy(evaluator = evaluator)
            return MockedCellExecutorImpl(CellExecutorImpl(hackedContext), evaluator.executedCodes, evaluator.results)
        }
    }
}

fun ReplForJupyter.mockExecution() = TrackedCellExecutor.create(this, true)

fun ReplForJupyter.trackExecution() = TrackedCellExecutor.create(this, false)

internal class MockedCellExecutorImpl(private val executor: CellExecutor, override val executedCodes: List<Code>, override val results: List<Any?>) : TrackedCellExecutor, CellExecutor by executor

interface TrackedInternalEvaluator : InternalEvaluator {
    val executedCodes: List<Code>
    val results: List<Any?>
}

internal class MockedInternalEvaluator : TrackedInternalEvaluator {
    override var logExecution: Boolean = false
    override var writeCompiledClasses: Boolean = false
    override var serializeScriptData: Boolean = false
    override val lastKClass: KClass<*> = Unit::class
    override val lastClassLoader: ClassLoader = ClassLoader.getSystemClassLoader()
    override val executedCodes = mutableListOf<Code>()

    override val variablesHolder = mutableMapOf<String, VariableState>()
    override val cellVariables = mutableMapOf<Int, MutableSet<String>>()

    override val results: List<Any?>
        get() = executedCodes.map { null }

    override fun eval(
        code: Code,
        compilingOptions: JupyterCompilingOptions,
        evaluatorWorkflowListener: EvaluatorWorkflowListener?,
    ): InternalEvalResult {
        executedCodes.add(code.trimIndent())
        return InternalEvalResult(FieldValue(null, null), Unit)
    }
}

internal class TrackedInternalEvaluatorImpl(private val baseEvaluator: InternalEvaluator) : TrackedInternalEvaluator, InternalEvaluator by baseEvaluator {

    override val executedCodes = mutableListOf<Code>()

    override val results = mutableListOf<Any?>()

    override fun eval(
        code: Code,
        compilingOptions: JupyterCompilingOptions,
        evaluatorWorkflowListener: EvaluatorWorkflowListener?,
    ): InternalEvalResult {
        executedCodes.add(code.trimIndent())
        val res = baseEvaluator.eval(code, compilingOptions, evaluatorWorkflowListener)
        results.add(res.result.value)
        return res
    }
}
