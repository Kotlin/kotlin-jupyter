package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.Result
import jupyter.kotlin.textResult
import org.jetbrains.kotlin.cli.common.repl.ReplEvalResult
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream

val ReplEvalResult.asResult: Result get() = when (this) {
    is ReplEvalResult.ValueResult -> {
        if (value is Result) value as Result
        else textResult(
                try {
                    value.toString()
                } catch (e: Exception) {
                    "Unable to convert result to string: $e"
                })
    }
    is ReplEvalResult.UnitResult -> textResult("Ok")
    is ReplEvalResult.Error -> textResult(message)
    is ReplEvalResult.Incomplete -> textResult("error: incomplete code")
    else -> throw Exception("Unexpected result from eval call: $this")
}

class ForkingOutputStream(val stdout: PrintStream, val publish: PrintStream, val captureOutput: Boolean) : OutputStream() {
    val capturedOutput = ByteArrayOutputStream()

    override fun write(b: Int) {
        stdout.write(b)
        publish.write(b)
        if (captureOutput) capturedOutput.write(b)
    }
}

fun JupyterConnection.evalWithIO(body: () -> ReplEvalResult): ReplEvalResult {
    val out = System.out
    val err = System.err

    // TODO: make configuration option of whether to pipe back stdout and stderr
    // TODO: make a configuration option to limit the total stdout / stderr possibly returned (in case it goes wild...)
    val forkedOut = ForkingOutputStream(out, iopubOut, true)
    val forkedError = ForkingOutputStream(err, iopubErr, false)

    System.setOut(PrintStream(forkedOut, true, "UTF-8"))
    System.setErr(PrintStream(forkedError, true, "UTF-8"))

    val `in` = System.`in`
    System.setIn(stdinIn)
    try {
        return body().let {
            // TODO: make this a configuration option to pass back the stdout as the value if Unit (notebooks do not display the stdout, only console does)
            if (it is ReplEvalResult.UnitResult) {
                ReplEvalResult.ValueResult(it.updatedHistory, String(forkedOut.capturedOutput.toByteArray()))
            } else {
                it
            }
        }

    }
    finally {
        System.setIn(`in`)
        System.setErr(err)
        System.setOut(out)
    }
}