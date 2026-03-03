package org.jetbrains.kotlinx.jupyter.magics.contexts

/**
 * Context interface for handlers that need to work with multiple contexts.
 */
class CompositeMagicHandlerContext(
    val contexts: List<MagicHandlerContext>,
) : MagicHandlerContext
