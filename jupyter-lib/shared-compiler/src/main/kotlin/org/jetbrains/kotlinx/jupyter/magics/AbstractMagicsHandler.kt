package org.jetbrains.kotlinx.jupyter.magics

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.types.choice
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException

abstract class AbstractMagicsHandler : MagicsHandler {
    protected var arg: String? = null
    protected var tryIgnoreErrors: Boolean = false
    protected var parseOnly: Boolean = false

    protected fun argumentsList() = arg?.trim()?.takeIf { it.isNotEmpty() }?.split(" ") ?: emptyList()
    protected fun handleSingleOptionalFlag(action: (Boolean?) -> Unit) {
        object : CliktCommand() {
            val arg by nullableFlag()
            override fun run() {
                action(arg)
            }
        }.parse(argumentsList())
    }

    private val callbackMap: Map<ReplLineMagic, () -> Unit> = mapOf(
        ReplLineMagic.USE to ::handleUse,
        ReplLineMagic.TRACK_CLASSPATH to ::handleTrackClasspath,
        ReplLineMagic.TRACK_EXECUTION to ::handleTrackExecution,
        ReplLineMagic.DUMP_CLASSES_FOR_SPARK to ::handleDumpClassesForSpark,
        ReplLineMagic.USE_LATEST_DESCRIPTORS to ::handleUseLatestDescriptors,
        ReplLineMagic.OUTPUT to ::handleOutput,
        ReplLineMagic.LOG_LEVEL to ::handleLogLevel,
        ReplLineMagic.LOG_HANDLER to ::handleLogHandler,
    )

    override fun handle(magicText: String, tryIgnoreErrors: Boolean, parseOnly: Boolean) {
        try {
            val parts = magicText.split(' ', limit = 2)
            val keyword = parts[0]
            val arg = if (parts.count() > 1) parts[1] else null

            val magic = if (parseOnly) null else ReplLineMagic.valueOfOrNull(keyword)?.value
            if (magic == null && !parseOnly && !tryIgnoreErrors) {
                throw ReplPreprocessingException("Unknown line magic keyword: '$keyword'")
            }

            if (magic != null) {
                handle(magic, arg, tryIgnoreErrors, parseOnly)
            }
        } catch (e: Exception) {
            throw ReplPreprocessingException("Failed to process '%$magicText' command. " + e.message, e)
        }
    }

    fun handle(magic: ReplLineMagic, arg: String?, tryIgnoreErrors: Boolean, parseOnly: Boolean) {
        val callback = callbackMap[magic] ?: throw UnhandledMagicException(magic, this)

        this.arg = arg
        this.tryIgnoreErrors = tryIgnoreErrors
        this.parseOnly = parseOnly

        callback()
    }

    open fun handleUse() {}
    open fun handleTrackClasspath() {}
    open fun handleTrackExecution() {}
    open fun handleDumpClassesForSpark() {}
    open fun handleUseLatestDescriptors() {}
    open fun handleOutput() {}
    open fun handleLogLevel() {}
    open fun handleLogHandler() {}

    companion object {
        fun CliktCommand.nullableFlag() = argument().choice(mapOf("on" to true, "off" to false)).optional()
    }
}
