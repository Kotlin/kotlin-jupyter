package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.Result
import jupyter.kotlin.textResult

val ReplForJupyter.EvalResult.asResult: Result get() = when (this) {
    is ReplForJupyter.EvalResult.ValueResult -> {
        if (value is Result) value
        else textResult(
                try {
                    value.toString()
                } catch (e: Exception) {
                    "Unable to convert result to string: $e"
                })
    }
    is ReplForJupyter.EvalResult.UnitResult -> textResult("Ok")
    is ReplForJupyter.EvalResult.Error -> textResult(errorText)
    is ReplForJupyter.EvalResult.Incomplete -> textResult("error: incomplete code")
    else -> throw Exception("Unexpected result from eval call: $this")
}
