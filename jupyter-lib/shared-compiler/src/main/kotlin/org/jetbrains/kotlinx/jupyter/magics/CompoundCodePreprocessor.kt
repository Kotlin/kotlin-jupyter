package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.api.CodePreprocessor
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost

/**
 * Containing [preprocessors]' [process] are run in reversed order: last added processors
 * are run first
 */
class CompoundCodePreprocessor(
    private val preprocessors: MutableList<CodePreprocessor>
) : CodePreprocessor {
    constructor(vararg preprocessors: CodePreprocessor) : this(preprocessors.toMutableList())

    override fun process(code: String, host: KotlinKernelHost): CodePreprocessor.Result {
        return preprocessors.foldRight(CodePreprocessor.Result(code, emptyList())) { preprocessor, result ->
            if (preprocessor.accepts(result.code)) {
                val newResult = preprocessor.process(result.code, host)
                CodePreprocessor.Result(newResult.code, result.libraries + newResult.libraries)
            } else result
        }
    }

    fun add(preprocessor: CodePreprocessor) {
        preprocessors.add(preprocessor)
    }

    fun addAll(preprocessors: Iterable<CodePreprocessor>) {
        this.preprocessors.addAll(preprocessors)
    }
}
