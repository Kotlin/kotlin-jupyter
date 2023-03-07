package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.api.CodePreprocessor
import org.jetbrains.kotlinx.jupyter.api.CodePreprocessorsProcessor
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.exceptions.KernelInternalObject
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.rethrowAsLibraryException
import org.jetbrains.kotlinx.jupyter.util.PriorityList

/**
 * Containing [preprocessors]' [process] are run in reversed order: last added processors
 * are run first
 */
class CompoundCodePreprocessor(vararg preprocessors: CodePreprocessor) : CodePreprocessor, CodePreprocessorsProcessor {

    private val preprocessors = PriorityList<CodePreprocessor>()

    init {
        for (preprocessor in preprocessors) {
            register(preprocessor)
        }
    }

    override fun process(code: String, host: KotlinKernelHost): CodePreprocessor.Result {
        var result = CodePreprocessor.Result(code, emptyList())

        fun iteration(preprocessor: CodePreprocessor) {
            if (preprocessor.accepts(result.code)) {
                val newResult = preprocessor.process(result.code, host)
                result = CodePreprocessor.Result(newResult.code, result.libraries + newResult.libraries)
            }
        }

        for (preprocessor in preprocessors) {
            if (preprocessor is KernelInternalObject) {
                iteration(preprocessor)
            } else {
                rethrowAsLibraryException(LibraryProblemPart.CODE_PREPROCESSORS) {
                    iteration(preprocessor)
                }
            }
        }
        return result
    }

    override fun register(preprocessor: CodePreprocessor, priority: Int) {
        preprocessors.add(preprocessor, priority)
    }

    override fun unregister(preprocessor: CodePreprocessor) {
        preprocessors.remove(preprocessor)
    }

    override fun unregisterAll() {
        preprocessors.clear()
    }

    override fun registeredPreprocessors(): Collection<CodePreprocessor> {
        return preprocessors.elements()
    }

    override fun registeredPreprocessorsWithPriority(): List<Pair<CodePreprocessor, Int>> {
        return preprocessors.elementsWithPriority()
    }
}
