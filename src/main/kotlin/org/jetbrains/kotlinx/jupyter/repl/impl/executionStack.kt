package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition

// Immutable interface representing stack frame
interface ExecutionStackFrame {
    val previous: ExecutionStackFrame?
    val libraries: List<LibraryDefinition>
}

// Mutable stack frame. Mutation is only available for this specific frame
class MutableExecutionStackFrame(
    override val previous: ExecutionStackFrame? = null
) : ExecutionStackFrame {
    override val libraries = mutableListOf<LibraryDefinition>()
}

fun ExecutionStackFrame?.traverseStack() = generateSequence(this) { it.previous }
fun ExecutionStackFrame?.push() = MutableExecutionStackFrame(this)

val ExecutionStackFrame?.libraryOptions: Map<String, String> get() {
    return buildMap {
        traverseStack().forEach { frame ->
            frame.libraries.forEach { library ->
                library.options.entries.forEach { (key, value) ->
                    put(key, value)
                }
            }
        }
    }
}
