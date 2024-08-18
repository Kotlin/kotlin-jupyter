package org.jetbrains.kotlinx.jupyter.api

import org.jetbrains.kotlinx.jupyter.util.FilteringClassLoader

interface KernelRunMode {
    val name: String

    /**
     * Creates an intermediary ClassLoader between the parent classloader (which is
     * usually a classloader that loads kernel classpath) and the chain of REPL classloaders,
     * including the base one. If null is returned, no intermediate classloader will be used.
     */
    fun createIntermediaryClassLoader(parent: ClassLoader): ClassLoader?

    val shouldKillProcessOnShutdown: Boolean

    val inMemoryOutputsSupported: Boolean

    val isRunInsideIntellijProcess: Boolean
}

abstract class AbstractKernelRunMode(override val name: String) : KernelRunMode {
    override fun toString(): String {
        return name
    }
}

object StandaloneKernelRunMode : AbstractKernelRunMode("Standalone") {
    override fun createIntermediaryClassLoader(parent: ClassLoader) = createDefaultFilteringClassLoader(parent)

    override val shouldKillProcessOnShutdown: Boolean get() = true
    override val inMemoryOutputsSupported: Boolean get() = false
    override val isRunInsideIntellijProcess: Boolean get() = false
}

object EmbeddedKernelRunMode : AbstractKernelRunMode("Embedded") {
    override fun createIntermediaryClassLoader(parent: ClassLoader) = null

    override val shouldKillProcessOnShutdown: Boolean get() = false
    override val inMemoryOutputsSupported: Boolean get() = false
    override val isRunInsideIntellijProcess: Boolean get() = false
}

fun createDefaultFilteringClassLoader(parent: ClassLoader): ClassLoader {
    return FilteringClassLoader(parent) { fqn ->
        listOf(
            "jupyter.kotlin.",
            "org.jetbrains.kotlinx.jupyter.api",
            "kotlin.",
            "kotlinx.serialization.",
        ).any { fqn.startsWith(it) } ||
            (fqn.startsWith("org.jetbrains.kotlin.") && !fqn.startsWith("org.jetbrains.kotlinx.jupyter."))
    }
}
