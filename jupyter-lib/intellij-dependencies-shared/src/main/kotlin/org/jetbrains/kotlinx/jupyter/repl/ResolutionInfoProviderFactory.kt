package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.libraries.LibraryHttpUtil
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory

/**
 * Creates new [ResolutionInfoProvider]s given other REPL components such as [org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory]
 */
fun interface ResolutionInfoProviderFactory {
    fun create(
        httpUtil: LibraryHttpUtil,
        loggerFactory: KernelLoggerFactory,
    ): ResolutionInfoProvider
}
