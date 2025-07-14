package org.jetbrains.kotlinx.jupyter.magics.contexts

/**
 * Context interface for basic command handling functionality.
 * Provides access to command arguments and flags.
 */
class CommandHandlingMagicHandlerContext : MagicHandlerContext {
    /**
     * The current command argument.
     */
    var arg: String? = null

    /**
     * Flag indicating whether to try to ignore errors.
     */
    var tryIgnoreErrors: Boolean = false

    /**
     * Flag indicating whether to only parse the command without executing it.
     */
    var parseOnly: Boolean = false

    /**
     * Splits the current argument into a list of arguments.
     */
    fun argumentsList(): List<String> = arg?.trim()?.takeIf { it.isNotEmpty() }?.split(" ") ?: emptyList()
}
