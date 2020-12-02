package org.jetbrains.kotlin.jupyter.magics

import org.jetbrains.kotlin.jupyter.api.LibraryDefinitionProducer
import org.jetbrains.kotlin.jupyter.common.ReplLineMagic

abstract class AbstractMagicsHandler : MagicsHandler {
    protected var arg: String? = null
    protected var tryIgnoreErrors: Boolean = false
    protected var parseOnly: Boolean = false

    protected val newLibraries: MutableList<LibraryDefinitionProducer> = mutableListOf()

    private val callbackMap: Map<ReplLineMagic, () -> Unit> = mapOf(
        ReplLineMagic.use to ::handleUse,
        ReplLineMagic.trackClasspath to ::handleTrackClasspath,
        ReplLineMagic.trackExecution to ::handleTrackExecution,
        ReplLineMagic.dumpClassesForSpark to ::handleDumpClassesForSpark,
        ReplLineMagic.useLatestDescriptors to ::handleUseLatestDescriptors,
        ReplLineMagic.output to ::handleOutput,
    )

    override fun handle(magic: ReplLineMagic, arg: String?, tryIgnoreErrors: Boolean, parseOnly: Boolean) {
        val callback = callbackMap[magic] ?: throw UnhandledMagicException(magic, this)

        this.arg = arg
        this.tryIgnoreErrors = tryIgnoreErrors
        this.parseOnly = parseOnly

        callback()
    }

    override fun getLibraries(): List<LibraryDefinitionProducer> {
        val librariesCopy = newLibraries.toList()
        newLibraries.clear()
        return librariesCopy
    }

    open fun handleUse() {}
    open fun handleTrackClasspath() {}
    open fun handleTrackExecution() {}
    open fun handleDumpClassesForSpark() {}
    open fun handleUseLatestDescriptors() {}
    open fun handleOutput() {}
}
