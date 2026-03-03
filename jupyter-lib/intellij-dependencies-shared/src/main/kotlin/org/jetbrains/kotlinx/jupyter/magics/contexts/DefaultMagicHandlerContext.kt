package org.jetbrains.kotlinx.jupyter.magics.contexts

import org.jetbrains.kotlinx.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher
import org.jetbrains.kotlinx.jupyter.repl.ReplOptions
import org.jetbrains.kotlinx.jupyter.repl.logging.LoggingManager

/**
 * Creates a context for handling default magics.
 * This context might be used to create all handlers
 * of [org.jetbrains.kotlinx.jupyter.common.ReplLineMagic]s
 */
fun createDefaultMagicHandlerContext(
    librariesProcessor: LibrariesProcessor,
    libraryResolutionInfoSwitcher: ResolutionInfoSwitcher<DefaultInfoSwitch>,
    replOptions: ReplOptions,
    loggingManager: LoggingManager,
) = CompositeMagicHandlerContext(
    listOf(
        LibrariesMagicHandlerContext(librariesProcessor, libraryResolutionInfoSwitcher),
        ReplOptionsMagicHandlerContext(replOptions),
        LoggingMagicHandlerContext(loggingManager),
        CommandHandlingMagicHandlerContext(),
    ),
)
