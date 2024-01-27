package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.common.getHttp
import org.jetbrains.kotlinx.jupyter.common.successful
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.config.getLogger
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptorsProvider
import org.jetbrains.kotlinx.jupyter.log
import kotlin.script.experimental.api.SourceCodeCompletionVariant

class CompletionMagicsProcessor(
    libraryDescriptorsProvider: LibraryDescriptorsProvider,
    parseOutCellMarker: Boolean = false,
) : AbstractCompletionMagicsProcessor<SourceCodeCompletionVariant>(libraryDescriptorsProvider, parseOutCellMarker) {

    override fun variant(text: String, icon: String) = SourceCodeCompletionVariant(text, text, icon, icon)
    override fun key(variant: SourceCodeCompletionVariant) = variant.text

    override fun getHttpResponseText(url: String): String? {
        val response = getHttp(url)
        val status = response.status
        if (!status.successful) {
            getLogger("magics completion").warn("Magic completion request failed: $status")
            return null
        }
        return response.text
    }

    fun process(code: String, cursor: Int): Result {
        val magics = magicsIntervals(code)
        var insideMagic = false
        val handler = Handler()

        for (magicRange in magics) {
            if (cursor in (magicRange.from + 1)..magicRange.to) {
                insideMagic = true
                if (code[magicRange.from] != MAGICS_SIGN || cursor == magicRange.from) continue

                val magicText = code.substring(magicRange.from + 1, magicRange.to)
                log.catchAll(msg = "Handling completion of $magicText failed") {
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
