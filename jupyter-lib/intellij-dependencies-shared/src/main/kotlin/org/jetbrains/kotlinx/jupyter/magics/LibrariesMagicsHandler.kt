package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException
import org.jetbrains.kotlinx.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlinx.jupyter.magics.contexts.CommandHandlingMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.LibrariesMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.MagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.requireContext

/**
 * Handler for library-related magic commands.
 * Handles %use and %useLatestDescriptors commands.
 */
class LibrariesMagicsHandler(
    context: MagicHandlerContext,
) : BasicMagicsHandler(context) {
    private val librariesContext = context.requireContext<LibrariesMagicHandlerContext>()

    override val callbackMap: Map<ReplLineMagic, () -> Unit> =
        mapOf(
            ReplLineMagic.USE to ::handleUse,
            ReplLineMagic.USE_LATEST_DESCRIPTORS to ::handleUseLatestDescriptors,
        )

    /**
     * Handles the %use command, which loads libraries.
     */
    private fun handleUse() {
        try {
            commandHandlingContext.arg?.let { notNullArg ->
                val newLibraries = librariesContext.librariesProcessor.processNewLibraries(notNullArg)
                librariesContext.addLibraries(newLibraries)
            } ?: throw ReplPreprocessingException("Need some arguments for 'use' command")
        } catch (e: Exception) {
            if (!commandHandlingContext.tryIgnoreErrors) throw e
        }
    }

    /**
     * Handles the %useLatestDescriptors command, which toggles between using classpath or git reference for library resolution.
     */
    private fun handleUseLatestDescriptors() {
        handleSingleOptionalFlag {
            librariesContext.libraryResolutionInfoSwitcher.switch =
                if (it == false) {
                    DefaultInfoSwitch.CLASSPATH
                } else {
                    DefaultInfoSwitch.GIT_REFERENCE
                }
        }
    }

    companion object : MagicHandlerFactoryImpl(
        ::LibrariesMagicsHandler,
        listOf(
            LibrariesMagicHandlerContext::class,
            CommandHandlingMagicHandlerContext::class,
        ),
    )
}
