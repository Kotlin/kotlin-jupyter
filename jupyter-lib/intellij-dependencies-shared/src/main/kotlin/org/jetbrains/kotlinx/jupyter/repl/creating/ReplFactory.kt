package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter

interface ReplFactory {
    fun createRepl(): ReplForJupyter
}
