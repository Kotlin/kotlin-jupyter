package org.jetbrains.kotlinx.jupyter.compiler.impl

import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerServiceProvider
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory

/**
 * In-process compiler service provider.
 * This provider has lower priority (100) and is used as a fallback
 * when the daemon provider is not available, or for testing.
 */
class InProcessCompilerServiceProvider : CompilerServiceProvider {
    override val priority: Int
        get() = priorityOverride.get() ?: 100

    override fun createCompiler(
        params: CompilerParams,
        callbacks: KernelCallbacks,
        loggerFactory: KernelLoggerFactory,
    ): CompilerService = CompilerServiceImpl(params, callbacks, loggerFactory)

    companion object {
        private val priorityOverride = ThreadLocal<Int?>()

        /**
         * Sets the priority override for the current thread.
         * Use this in tests to control which compiler provider is selected.
         * Higher priority values are preferred over lower ones.
         */
        fun setPriority(priority: Int?) {
            if (priority == null) {
                priorityOverride.remove()
            } else {
                priorityOverride.set(priority)
            }
        }

        /**
         * Executes the given block with a temporary priority override.
         */
        inline fun <T> withPriority(
            priority: Int,
            block: () -> T,
        ): T {
            setPriority(priority)
            try {
                return block()
            } finally {
                setPriority(null)
            }
        }
    }
}
