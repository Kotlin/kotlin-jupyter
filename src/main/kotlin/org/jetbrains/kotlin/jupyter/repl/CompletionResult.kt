package org.jetbrains.kotlin.jupyter.repl

import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.jupyter.CompleteReply
import org.jetbrains.kotlin.jupyter.ErrorReply
import org.jetbrains.kotlin.jupyter.MessageContent
import org.jetbrains.kotlin.jupyter.Paragraph
import org.jetbrains.kotlin.jupyter.compiler.util.CodeInterval
import kotlin.script.experimental.api.SourceCodeCompletionVariant

abstract class CompletionResult {
    abstract val message: MessageContent

    open class Success(
        private val matches: List<String>,
        private val bounds: CodeInterval,
        private val metadata: List<SourceCodeCompletionVariant>,
        private val text: String,
        private val cursor: Int
    ) : CompletionResult() {
        init {
            assert(matches.size == metadata.size)
        }

        override val message: MessageContent
            get() = CompleteReply(
                matches,
                bounds.from,
                bounds.to,
                Paragraph(cursor, text),
                CompleteReply.Metadata(
                    metadata.map {
                        CompleteReply.ExperimentalType(
                            it.text,
                            it.tail,
                            bounds.from,
                            bounds.to
                        )
                    },
                    metadata.map {
                        CompleteReply.ExtendedMetadataEntry(
                            it.text,
                            it.displayText,
                            it.icon,
                            it.tail
                        )
                    }
                )
            )

        @TestOnly
        fun sortedMatches(): List<String> = matches.sorted()
    }

    class Empty(
        text: String,
        cursor: Int
    ) : Success(emptyList(), CodeInterval(cursor, cursor), emptyList(), text, cursor)

    class Error(
        private val errorName: String,
        private val errorValue: String,
        private val traceBack: List<String>
    ) : CompletionResult() {
        override val message: MessageContent
            get() = ErrorReply(errorName, errorValue, traceBack)
    }
}
