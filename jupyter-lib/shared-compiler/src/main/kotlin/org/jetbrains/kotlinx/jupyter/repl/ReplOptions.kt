package org.jetbrains.kotlinx.jupyter.repl

interface ReplOptions {
    var trackClasspath: Boolean
    var executedCodeLogging: ExecutedCodeLogging
    var writeCompiledClasses: Boolean
    var outputConfig: OutputConfig
}
