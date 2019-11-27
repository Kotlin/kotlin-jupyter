package org.jetbrains.kotlin.jupyter.magic

interface CodePreprocessor {
    fun process(code: String): String
}

class CompoundCodePreprocessor(private vararg val processors: CodePreprocessor) : CodePreprocessor {

    override fun process(code: String): String =
            processors.fold(code) { c, p -> p.process(c) }

}

class DelegatedCodePreprocessor(val processor: (String) -> String) : CodePreprocessor {
    override fun process(code: String) =
            processor(code)
}