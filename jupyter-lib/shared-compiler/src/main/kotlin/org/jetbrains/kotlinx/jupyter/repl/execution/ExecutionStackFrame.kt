package org.jetbrains.kotlinx.jupyter.repl.execution

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition

// Immutable interface representing stack frame
interface ExecutionStackFrame {
    val previous: ExecutionStackFrame?
    val libraries: List<LibraryDefinition>
}
