package org.jetbrains.kotlin.jupyter.magics

import org.jetbrains.kotlin.jupyter.compiler.util.ReplCompilerException
import org.jetbrains.kotlin.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlin.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlin.jupyter.libraries.LibraryFactoryDefaultInfoSwitcher

open class LibrariesOnlyMagicsHandler(
    private val librariesProcessor: LibrariesProcessor,
    private val libraryResolutionInfoSwitcher: LibraryFactoryDefaultInfoSwitcher<DefaultInfoSwitch>,
) : AbstractMagicsHandler() {
    override fun handleUse() {
        try {
            arg?.let { notNullArg ->
                newLibraries.addAll(librariesProcessor.processNewLibraries(notNullArg))
            } ?: throw ReplCompilerException("Need some arguments for 'use' command")
        } catch (e: Exception) {
            if (!tryIgnoreErrors) throw e
        }
    }

    override fun handleUseLatestDescriptors() {
        libraryResolutionInfoSwitcher.switch = when (arg?.trim()) {
            "-on" -> DefaultInfoSwitch.GIT_REFERENCE
            "-off" -> DefaultInfoSwitch.DIRECTORY
            else -> DefaultInfoSwitch.GIT_REFERENCE
        }
    }
}
