package org.jetbrains.kotlinx.jupyter.magics

class DefaultMagicHandlerFactoryProvider : MagicHandlerFactoryProvider {
    override fun provideFactories(): Collection<MagicHandlerFactory> =
        listOf(
            LogbackLoggingMagicsHandler,
        )
}
