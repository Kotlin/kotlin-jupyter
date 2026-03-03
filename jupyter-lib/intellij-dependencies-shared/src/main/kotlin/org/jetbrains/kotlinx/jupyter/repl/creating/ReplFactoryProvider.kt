package org.jetbrains.kotlinx.jupyter.repl.creating

interface ReplFactoryProvider {
    fun createReplFactory(componentsProvider: ReplComponentsProvider): ReplFactory
}
