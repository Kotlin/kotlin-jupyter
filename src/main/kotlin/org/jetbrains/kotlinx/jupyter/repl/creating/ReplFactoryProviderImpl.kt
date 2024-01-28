package org.jetbrains.kotlinx.jupyter.repl.creating

class ReplFactoryProviderImpl : ReplFactoryProvider {
    override fun createReplFactory(componentsProvider: ReplComponentsProvider): ReplFactory {
        return ReplFactoryBase(componentsProvider)
    }
}
