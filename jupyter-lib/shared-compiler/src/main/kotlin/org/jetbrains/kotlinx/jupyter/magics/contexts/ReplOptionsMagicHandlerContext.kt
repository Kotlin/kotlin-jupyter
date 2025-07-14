package org.jetbrains.kotlinx.jupyter.magics.contexts

import org.jetbrains.kotlinx.jupyter.repl.ReplOptions

/**
 * Context interface for handlers that need access to REPL options.
 * Provides access to the ReplOptions object for configuring the REPL environment.
 */
class ReplOptionsMagicHandlerContext(
    /**
     * Options for configuring the REPL environment.
     */
    val replOptions: ReplOptions,
) : MagicHandlerContext
