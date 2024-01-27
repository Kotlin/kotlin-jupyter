package org.jetbrains.kotlinx.jupyter.repl

import java.io.File

interface ReplOptions {
    val currentBranch: String
    val librariesDir: File

    var trackClasspath: Boolean
    var executedCodeLogging: ExecutedCodeLogging
    var writeCompiledClasses: Boolean
    var outputConfig: OutputConfig
    val debugPort: Int?
}
