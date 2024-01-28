package org.jetbrains.kotlinx.jupyter.repl.creating

import java.util.*

fun loadDefaultReplFactory(
    replComponentsProvider: ReplComponentsProvider,
    classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
): ReplFactory {
    val factoryProviders = ServiceLoader.load(ReplFactoryProvider::class.java, classLoader).toList()
    require(factoryProviders.isNotEmpty()) {
        "No REPL factory providers are available on the current classpath"
    }

    val factoryProvider = factoryProviders.first()
    return factoryProvider.createReplFactory(replComponentsProvider)
}
