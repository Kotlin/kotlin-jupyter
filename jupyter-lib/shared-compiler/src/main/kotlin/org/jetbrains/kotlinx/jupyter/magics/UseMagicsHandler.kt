package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException
import org.jetbrains.kotlinx.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher

open class UseMagicsHandler(
    private val librariesProcessor: LibrariesProcessor,
    private val libraryResolutionInfoSwitcher: ResolutionInfoSwitcher<DefaultInfoSwitch>,
) : LibrariesAwareAbstractMagicsHandler() {
    override fun handleUse() {
        try {
            arg?.let { notNullArg ->
                newLibraries.addAll(librariesProcessor.processNewLibraries(notNullArg))
            } ?: throw ReplPreprocessingException("Need some arguments for 'use' command")
        } catch (e: Exception) {
            if (!tryIgnoreErrors) throw e
        }
    }

    override fun handleUseLatestDescriptors() {
        handleSingleOptionalFlag {
            libraryResolutionInfoSwitcher.switch = if (it == false) DefaultInfoSwitch.CLASSPATH
            else DefaultInfoSwitch.GIT_REFERENCE
        }
    }
}
