package org.jetbrains.kotlinx.jupyter.test.repl

import org.jetbrains.kotlinx.jupyter.DisplayHandler
import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.api.Code

abstract class AbstractSingleReplTest : AbstractReplTest() {
    protected abstract val repl: ReplForJupyter

    protected fun eval(code: Code, displayHandler: DisplayHandler? = null, jupyterId: Int = -1) =
        repl.eval(code, displayHandler, jupyterId, jupyterId > 0)
}
