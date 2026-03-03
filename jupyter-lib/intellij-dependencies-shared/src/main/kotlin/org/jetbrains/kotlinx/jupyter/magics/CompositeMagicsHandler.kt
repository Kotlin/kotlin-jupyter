package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer
import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException
import org.jetbrains.kotlinx.jupyter.magics.contexts.LibrariesMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.MagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.getContext

/**
 * Registry for magic handlers.
 * Manages the registration of handlers and dispatches magic commands to the appropriate handler.
 */
class CompositeMagicsHandler(
    private val context: MagicHandlerContext,
) : LibrariesAwareMagicsHandler {
    private val handlers = mutableListOf<MagicsHandler>()

    /**
     * Registers a handler with the registry.
     * The handler will be used to handle magic commands if its required context types are satisfied by the registry's context.
     */
    fun createAndRegister(handlerFactories: List<MagicHandlerFactory>) {
        for (factory in handlerFactories) {
            val handler = factory.createIfApplicable(context) ?: continue
            handlers.add(handler)
        }
    }

    /**
     * Handles a magic command by dispatching it to the appropriate handler.
     */
    override fun handle(
        magicText: String,
        tryIgnoreErrors: Boolean,
        parseOnly: Boolean,
    ) {
        // Try each handler in order
        for (handler in handlers) {
            try {
                handler.handle(magicText, tryIgnoreErrors, parseOnly)
                return
            } catch (_: UnhandledMagicException) {
                // This handler doesn't support this magic, try the next one
                continue
            }
        }

        // If no handler handled the command, and we're not ignoring errors, throw an exception
        if (!tryIgnoreErrors && !parseOnly) {
            throw ReplPreprocessingException("There are no handlers registered for $magicText")
        }
    }

    /**
     * Gets the list of libraries added by magic commands.
     */
    override fun getLibraries(): List<LibraryDefinitionProducer> =
        context.getContext<LibrariesMagicHandlerContext>()?.getLibraries().orEmpty()
}
