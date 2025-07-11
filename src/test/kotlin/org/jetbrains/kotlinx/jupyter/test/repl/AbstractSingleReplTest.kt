package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.matchers.types.shouldBeTypeOf
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.closeIfPossible
import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCount
import org.jetbrains.kotlinx.jupyter.repl.EvalRequestData
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.repl.result.EvalResultEx
import org.jetbrains.kotlinx.jupyter.test.getOrFail
import org.junit.jupiter.api.AfterEach

abstract class AbstractSingleReplTest : AbstractReplTest() {
    protected abstract val repl: ReplForJupyter

    @AfterEach
    fun tearDown() {
        repl.closeIfPossible()
    }

    protected fun eval(
        code: Code,
        executionCount: Int = -1,
        storeHistory: Boolean = true,
    ) = repl.evalEx(EvalRequestData(code, ExecutionCount(executionCount), storeHistory))

    protected inline fun <reified T : Throwable> evalError(code: Code): T {
        val result = eval(code)
        result.shouldBeTypeOf<EvalResultEx.Error>()
        return result.error.shouldBeTypeOf<T>()
    }

    protected fun evalSuccess(code: Code) = eval(code).shouldBeTypeOf<EvalResultEx.Success>()

    protected fun completeOrFail(
        code: Code,
        cursor: Int,
    ) = repl.completeBlocking(code, cursor).getOrFail()

    protected fun complete(
        codeWithCursor: String,
        cursorSign: String = "|",
    ) = completeOrFail(
        codeWithCursor.replace(cursorSign, ""),
        codeWithCursor.indexOf(cursorSign),
    )

    protected fun listErrors(code: Code) = repl.listErrorsBlocking(code)
}
