package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlinx.jupyter.compiler.util.CodeInterval
import org.jetbrains.kotlinx.jupyter.messaging.CompleteReply
import org.jetbrains.kotlinx.jupyter.messaging.ErrorReply
import org.jetbrains.kotlinx.jupyter.messaging.MessageContent
import org.jetbrains.kotlinx.jupyter.messaging.Paragraph
import kotlin.script.experimental.api.SourceCodeCompletionVariant

abstract class CompletionResult {
    abstract val message: MessageContent

    open class Success(
        private val matches: List<String>,
        private val bounds: CodeInterval,
        private val metadata: List<SourceCodeCompletionVariant>,
        private val text: String,
        private val cursor: Int,
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
                            bounds.to,
                        )
                    },
                    metadata.map {
                        CompleteReply.ExtendedMetadataEntry(
                            it.text,
                            it.displayText,
                            it.icon,
                            it.tail,
                            it.deprecationLevel?.name,
                        )
                    },
                ),
            )

        @TestOnly
        fun sortedMatches(): List<String> = matches.sorted()

        @TestOnly
        fun matches(): List<String> = matches

        @TestOnly
        fun sortedRaw(): List<SourceCodeCompletionVariant> = metadata.sortedBy { it.text }
    }

    class Empty(
        text: String,
        cursor: Int,
    ) : Success(emptyList(), CodeInterval(cursor, cursor), emptyList(), text, cursor)

    class Error(
        private val errorName: String,
        private val errorValue: String,
        private val traceBack: List<String>,
    ) : CompletionResult() {
        override val message: MessageContent
            get() = ErrorReply(errorName, errorValue, traceBack)
    }
}
