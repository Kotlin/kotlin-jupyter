package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.compiler.util.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.libraries.DefaultInfoSwitch
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoSwitcher

open class LibrariesOnlyMagicsHandler(
    private val librariesProcessor: LibrariesProcessor,
    private val libraryResolutionInfoSwitcher: ResolutionInfoSwitcher<DefaultInfoSwitch>,
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
