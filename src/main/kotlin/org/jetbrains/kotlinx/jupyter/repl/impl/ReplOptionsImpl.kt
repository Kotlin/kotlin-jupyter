package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlinx.jupyter.repl.ExecutedCodeLogging
import org.jetbrains.kotlinx.jupyter.repl.InternalEvaluator
import org.jetbrains.kotlinx.jupyter.repl.OutputConfig
import org.jetbrains.kotlinx.jupyter.repl.ReplOptions

class ReplOptionsImpl(private val internalEvaluatorProvider: () -> InternalEvaluator) : ReplOptions {
    override var trackClasspath: Boolean = false

    private var outputConfigImpl = OutputConfig()
    override var outputConfig
        get() = outputConfigImpl
        set(value) {
            // reuse output config instance, because it is already passed to CapturingOutputStream and stream parameters should be updated immediately
            outputConfigImpl.update(value)
        }

    private var _executedCodeLogging: ExecutedCodeLogging = ExecutedCodeLogging.OFF
    override var executedCodeLogging: ExecutedCodeLogging
        get() = _executedCodeLogging
        set(value) {
            _executedCodeLogging = value
            internalEvaluatorProvider().logExecution = value != ExecutedCodeLogging.OFF
        }

    override var writeCompiledClasses: Boolean
        get() = internalEvaluatorProvider().writeCompiledClasses
        set(value) {
            internalEvaluatorProvider().writeCompiledClasses = value
        }
}
