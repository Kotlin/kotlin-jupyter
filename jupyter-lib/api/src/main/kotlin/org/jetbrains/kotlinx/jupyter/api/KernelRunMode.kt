package org.jetbrains.kotlinx.jupyter.api

import org.jetbrains.kotlinx.jupyter.util.createDefaultDelegatingClassLoader

/**
 * Represents settings that depend on the environment in which the kernel is running
 */
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

    val streamSubstitutionType: StreamSubstitutionType

    /**
     * Substitution happens only for the main thread in all non-standalone modes,
     * because there may be outputs not related to the cell being executed.
     *
     * In standalone mode, all outputs from threads should be printed.
     */
    val threadLocalStreamSubstitution: Boolean get() = true

    fun initializeSession(
        notebook: Notebook,
        evaluator: CodeEvaluator,
    ) = Unit
}

abstract class AbstractKernelRunMode(
    override val name: String,
) : KernelRunMode {
    override fun toString(): String = name
}

object StandaloneKernelRunMode : AbstractKernelRunMode("Standalone") {
    override fun createIntermediaryClassLoader(parent: ClassLoader) = createDefaultDelegatingClassLoader(parent)

    override val shouldKillProcessOnShutdown: Boolean get() = true
    override val inMemoryOutputsSupported: Boolean get() = false
    override val isRunInsideIntellijProcess: Boolean get() = false
    override val streamSubstitutionType: StreamSubstitutionType
        get() = StreamSubstitutionType.BLOCKING
    override val threadLocalStreamSubstitution: Boolean get() = false
}

object EmbeddedKernelRunMode : AbstractKernelRunMode("Embedded") {
    override fun createIntermediaryClassLoader(parent: ClassLoader) = null

    override val shouldKillProcessOnShutdown: Boolean get() = false
    override val inMemoryOutputsSupported: Boolean get() = false
    override val isRunInsideIntellijProcess: Boolean get() = false
    override val streamSubstitutionType: StreamSubstitutionType
        get() = StreamSubstitutionType.BLOCKING
    override val threadLocalStreamSubstitution: Boolean get() = true
}
