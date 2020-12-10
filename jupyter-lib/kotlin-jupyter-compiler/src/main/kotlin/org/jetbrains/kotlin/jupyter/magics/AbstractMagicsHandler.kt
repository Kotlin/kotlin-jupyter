package org.jetbrains.kotlin.jupyter.magics

import org.jetbrains.kotlin.jupyter.api.LibraryDefinitionProducer
import org.jetbrains.kotlin.jupyter.common.ReplLineMagic

abstract class AbstractMagicsHandler : MagicsHandler {
    protected var arg: String? = null
    protected var tryIgnoreErrors: Boolean = false
    protected var parseOnly: Boolean = false

    protected val newLibraries: MutableList<LibraryDefinitionProducer> = mutableListOf()

    private val callbackMap: Map<ReplLineMagic, () -> Unit> = mapOf(
        ReplLineMagic.USE to ::handleUse,
        ReplLineMagic.TRACK_CLASSPATH to ::handleTrackClasspath,
        ReplLineMagic.TRACK_EXECUTION to ::handleTrackExecution,
        ReplLineMagic.DUMP_CLASSES_FOR_SPARK to ::handleDumpClassesForSpark,
        ReplLineMagic.USE_LATEST_DESCRIPTORS to ::handleUseLatestDescriptors,
        ReplLineMagic.OUTPUT to ::handleOutput,
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
