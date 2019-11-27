package org.jetbrains.kotlin.jupyter.magic

import org.jetbrains.kotlin.jupyter.ReplCompilerException

interface MagicHandler {

    val keyword: String

    fun process(arguments: String?): String
}

open class DelegatedMagicHandler(override val keyword: String, val processArguments: (String?) -> String) : MagicHandler {

    override fun process(arguments: String?): String = processArguments(arguments)
}

class EnableOptionMagicHandler(keyword: String, enable: () -> Unit) : DelegatedMagicHandler(keyword, {
    enable()
    ""
})

class MagicProcessor(vararg handlers: MagicHandler) {

    private val handlersMap = handlers.map { it.keyword to it }.toMap()

    private fun processMagic(text: String): String? {
        val parts = text.split(' ', limit = 2)
        val keyword = parts[0]
        val arg = if (parts.count() > 1) parts[1] else null
        return handlersMap[keyword]?.process(arg)
    }

    fun replaceMagics(code: String): String {
        val sb = StringBuilder()
        var lastMagicIndex = 0
        var lastCopiedIndex = 0

        while (true) {
            val magicStart = code.indexOf("%", lastMagicIndex)
            if (magicStart == -1) {
                sb.append(code.substring(lastCopiedIndex))
                return sb.toString()
            }
            val magicEnd = code.indexOf('\n', magicStart).let { if (it == -1) code.length else it }
            val magicText = code.substring(magicStart + 1, magicEnd)
            try {
                val codeToInsert = processMagic(magicText)
                if (codeToInsert != null) {
                    sb.append(code.substring(lastCopiedIndex, magicStart))
                    sb.append(codeToInsert)
                    lastCopiedIndex = magicEnd
                    lastMagicIndex = magicEnd
                } else lastMagicIndex = magicStart + 1
            } catch (e: Exception) {
                throw ReplCompilerException("Failed to process '%$magicText' command. " + e.message)
            }
        }
    }
}