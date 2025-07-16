package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.common.HttpClient
import org.jetbrains.kotlinx.jupyter.common.getHttp
import org.jetbrains.kotlinx.jupyter.common.successful
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptorsProvider
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import kotlin.script.experimental.api.SourceCodeCompletionVariant

/**
 * This class is responsible for finding autocomplete results when starting a "magics" statement inside a notebook,
 * i.e., it triggers when the user starts a line with `%`.
 */
class CompletionMagicsProcessor(
    loggerFactory: KernelLoggerFactory,
    libraryDescriptorsProvider: LibraryDescriptorsProvider,
    parseOutCellMarker: Boolean = false,
    private val httpClient: HttpClient,
) : AbstractCompletionMagicsProcessor<SourceCodeCompletionVariant>(libraryDescriptorsProvider, parseOutCellMarker) {
    private val logger = loggerFactory.getLogger(this::class)

    override fun variant(
        text: String,
        icon: String,
    ) = SourceCodeCompletionVariant(text, text, icon, icon)

    override fun key(variant: SourceCodeCompletionVariant) = variant.text

    override fun getHttpResponseText(url: String): String? {
        val response = httpClient.getHttp(url)
        val status = response.status
        if (!status.successful) {
            logger.warn("Magic completion request failed: $status")
            return null
        }
        return response.text
    }

    fun process(
        code: String,
        cursor: Int,
    ): Result {
        val magics = magicsIntervals(code)
        var insideMagic = false
        val handler = Handler()

        for (magicRange in magics) {
            if (cursor in (magicRange.from + 1)..magicRange.to) {
                insideMagic = true
                if (code[magicRange.from] != MAGICS_SIGN || cursor == magicRange.from) continue

                val magicText = code.substring(magicRange.from + 1, magicRange.to)
                logger.catchAll(msg = "Handling completion of $magicText failed") {
                    handler.handle(magicText, cursor - magicRange.from - 1)
                }
            }
        }

        return Result(getCleanCode(code, magics), insideMagic, handler.completions)
    }

    class Result(
        val code: String,
        val cursorInsideMagic: Boolean,
        val completions: List<SourceCodeCompletionVariant>,
    )
}
