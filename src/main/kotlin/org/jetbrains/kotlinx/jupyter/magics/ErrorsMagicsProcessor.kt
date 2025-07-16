package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.compiler.util.CodeInterval
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.diagnostic
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import kotlin.script.experimental.api.ScriptDiagnostic

class ErrorsMagicsProcessor(
    loggerFactory: KernelLoggerFactory,
    parseOutCellMarker: Boolean = false,
) : AbstractMagicsProcessor(parseOutCellMarker) {
    val logger = loggerFactory.getLogger(this::class)

    fun process(code: String): Result {
        val magics = magicsIntervals(code)
        val handler = Handler(code)

        for (magicRange in magics) {
            if (code[magicRange.from] != MAGICS_SIGN) continue

            val magicText = code.substring(magicRange.from + 1, magicRange.to)
            logger.catchAll(msg = "Handling errors of $magicText failed") {
                handler.handle(magicText, magicRange)
            }
        }

        return Result(getCleanCode(code, magics), handler.diagnostics.asSequence())
    }

    class Result(
        val code: String,
        val diagnostics: Sequence<ScriptDiagnostic>,
    )

    private inner class Handler(
        val code: String,
    ) {
        private val _diagnostics = mutableListOf<ScriptDiagnostic>()
        val diagnostics: List<ScriptDiagnostic> get() = _diagnostics

        fun handle(
            magicText: String,
            magicRange: CodeInterval,
        ) {
            val magicName = magicText.substringBefore(' ').trim()
            val magic = ReplLineMagic.valueOfOrNull(magicName)

            if (magic == null) {
                addError("Unknown magic", magicRange)
                return
            }
        }

        fun addError(
            message: String,
            codeInterval: CodeInterval,
        ) {
            _diagnostics.add(
                codeInterval.diagnostic(code, message),
            )
        }
    }
}
