package org.jetbrains.kotlinx.jupyter.logging

import org.jetbrains.kotlinx.jupyter.magics.FullMagicsHandler
import org.jetbrains.kotlinx.jupyter.magics.LibrariesAwareMagicsHandler
import org.jetbrains.kotlinx.jupyter.repl.creating.LazilyConstructibleReplComponentsProvider
import org.jetbrains.kotlinx.jupyter.repl.creating.ReplComponentsProvider
import org.jetbrains.kotlinx.jupyter.repl.logging.LoggingManager

class ReplComponentsProviderWithLogbackManager(
    delegate: LazilyConstructibleReplComponentsProvider,
) : ReplComponentsProvider by delegate {
    override val magicsHandler: LibrariesAwareMagicsHandler by lazy {
        FullMagicsHandler(
            replOptions,
            librariesProcessor,
            libraryInfoSwitcher,
            loggingManager,
        )
    }
    override val loggingManager: LoggingManager by lazy {
        LogbackLoggingManager(loggerFactory)
    }
}
