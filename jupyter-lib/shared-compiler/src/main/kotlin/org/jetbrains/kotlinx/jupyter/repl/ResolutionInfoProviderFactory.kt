package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.libraries.LibraryHttpUtil
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider

fun interface ResolutionInfoProviderFactory {
    fun create(
        httpUtil: LibraryHttpUtil,
        loggerFactory: KernelLoggerFactory,
    ): ResolutionInfoProvider
}
