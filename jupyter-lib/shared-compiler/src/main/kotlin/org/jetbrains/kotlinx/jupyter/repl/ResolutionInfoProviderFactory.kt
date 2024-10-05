package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.libraries.LibraryHttpUtil
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider

/**
 * Creates new [ResolutionInfoProvider]s given other REPL components such as [KernelLoggerFactory]
 */
fun interface ResolutionInfoProviderFactory {
    fun create(
        httpUtil: LibraryHttpUtil,
        loggerFactory: KernelLoggerFactory,
    ): ResolutionInfoProvider
}
