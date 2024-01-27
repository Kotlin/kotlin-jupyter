package org.jetbrains.kotlinx.jupyter.repl

class EvalData(
    val executionCounter: Int,
    val rawCode: String,
) {
    constructor(evalRequestData: EvalRequestData) : this(evalRequestData.jupyterId, evalRequestData.code)
}
