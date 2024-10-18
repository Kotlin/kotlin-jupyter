package org.jetbrains.kotlinx.jupyter.spring.starter

import org.jetbrains.kotlinx.jupyter.api.CodeEvaluator
import org.jetbrains.kotlinx.jupyter.api.EmbeddedKernelRunMode
import org.jetbrains.kotlinx.jupyter.api.KernelRunMode
import org.jetbrains.kotlinx.jupyter.api.Notebook

object SpringProcessKernelRunMode : KernelRunMode by EmbeddedKernelRunMode {
    override fun initializeSession(
        notebook: Notebook,
        evaluator: CodeEvaluator,
    ) {
        notebook.sessionOptions.serializeScriptData = true
        evaluator.eval("1")
    }
}
