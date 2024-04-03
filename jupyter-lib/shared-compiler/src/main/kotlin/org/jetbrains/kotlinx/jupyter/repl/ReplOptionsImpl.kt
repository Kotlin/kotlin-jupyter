package org.jetbrains.kotlinx.jupyter.repl

class ReplOptionsImpl : ReplOptions {
    override var trackClasspath: Boolean = false

    private var _outputConfig = OutputConfig()
    override var outputConfig
        get() = _outputConfig
        set(value) {
            // reuse output config instance, because it is already passed to CapturingOutputStream and stream parameters should be updated immediately
            _outputConfig.update(value)
        }

    override var executedCodeLogging: ExecutedCodeLogging = ExecutedCodeLogging.OFF

    override var writeCompiledClasses: Boolean = false
}
