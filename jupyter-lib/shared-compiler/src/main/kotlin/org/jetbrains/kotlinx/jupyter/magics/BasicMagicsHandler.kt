package org.jetbrains.kotlinx.jupyter.magics

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.types.choice
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException
import org.jetbrains.kotlinx.jupyter.magics.contexts.CommandHandlingMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.MagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.requireContext

/**
 * Base implementation of MagicsHandler that uses the context-based approach.
 * This class provides basic parsing and dispatching of magic commands.
 *
 * @param context The context object that provides access to the required dependencies.
 */
abstract class BasicMagicsHandler(
    protected val context: MagicHandlerContext,
) : MagicsHandler {
    protected val commandHandlingContext = context.requireContext<CommandHandlingMagicHandlerContext>()

    /**
     * Map of magic commands to handler methods.
     */
    protected open val callbackMap: Map<ReplLineMagic, () -> Unit> = emptyMap()

    /**
     * Checks if this handler can handle the specified magic command.
     *
     * @param magic The magic command to check
     * @return true if this handler can handle the command, false otherwise
     */
    protected fun canHandle(magic: ReplLineMagic): Boolean = magic in callbackMap

    /**
     * Helper method for handling a single optional flag.
     * This method is only available if the context implements WithCommandHandling.
     */
    protected fun handleSingleOptionalFlag(action: (Boolean?) -> Unit) {
        object : CliktCommand() {
            val arg by nullableFlag()

            override fun run() {
                action(arg)
            }
        }.parse(commandHandlingContext.argumentsList())
    }

    override fun handle(
        magicText: String,
        tryIgnoreErrors: Boolean,
        parseOnly: Boolean,
    ) {
        try {
            val parts = magicText.split(' ', limit = 2)
            val keyword = parts[0]
            val arg = if (parts.count() > 1) parts[1] else null

            val magic = if (parseOnly) null else ReplLineMagic.valueOfOrNull(keyword)?.value
            if (magic == null && !parseOnly && !tryIgnoreErrors) {
                throw ReplPreprocessingException("Unknown line magic keyword: '$keyword'")
            }

            // Check if this handler can handle the magic command
            if (magic != null && !canHandle(magic)) {
                throw UnhandledMagicException(magic, this)
            }

            commandHandlingContext.arg = arg
            commandHandlingContext.tryIgnoreErrors = tryIgnoreErrors
            commandHandlingContext.parseOnly = parseOnly

            if (magic != null) {
                handle(magic)
            }
        } catch (e: Exception) {
            if (e is UnhandledMagicException) {
                throw e
            }
            throw ReplPreprocessingException("Failed to process '%$magicText' command. " + e.message, e)
        }
    }

    /**
     * Handles a specific magic command.
     *
     * @param magic The magic command to handle
     */
    protected fun handle(magic: ReplLineMagic) {
        val callback = callbackMap[magic] ?: throw UnhandledMagicException(magic, this)
        callback()
    }

    companion object {
        /**
         * Helper method for creating a nullable flag argument.
         */
        fun CliktCommand.nullableFlag() = argument().choice(mapOf("on" to true, "off" to false)).optional()
    }
}
