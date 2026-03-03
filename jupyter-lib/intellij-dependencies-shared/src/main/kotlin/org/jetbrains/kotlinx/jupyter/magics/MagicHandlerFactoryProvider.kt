package org.jetbrains.kotlinx.jupyter.magics

import java.util.ServiceLoader

/**
 * Provides [MagicHandlerFactory] instances.
 * Used with SPI to load extensions.
 */
interface MagicHandlerFactoryProvider {
    fun provideFactories(): Collection<MagicHandlerFactory>
}

class BasicMagicHandlerFactoryProvider : MagicHandlerFactoryProvider {
    override fun provideFactories(): Collection<MagicHandlerFactory> =
        listOf(
            LibrariesMagicsHandler,
            ReplOptionsMagicsHandler,
            LogLevelMagicsHandler,
        )
}

fun loadMagicHandlerFactories(classLoader: ClassLoader = Thread.currentThread().getContextClassLoader()): List<MagicHandlerFactory> =
    ServiceLoader
        .load(
            MagicHandlerFactoryProvider::class.java,
            classLoader,
        ).flatMap { it.provideFactories() }
