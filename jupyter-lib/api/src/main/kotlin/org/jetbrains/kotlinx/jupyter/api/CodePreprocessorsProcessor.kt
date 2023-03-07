package org.jetbrains.kotlinx.jupyter.api

interface CodePreprocessorsProcessor {
    fun register(preprocessor: CodePreprocessor) = register(preprocessor, ProcessingPriority.DEFAULT)
    fun register(preprocessor: CodePreprocessor, priority: Int)
    fun registerAll(preprocessors: Iterable<CodePreprocessor>) {
        for (execution in preprocessors) {
            register(execution)
        }
    }
    fun unregister(preprocessor: CodePreprocessor)
    fun unregisterAll()
    fun registeredPreprocessors(): Collection<CodePreprocessor>
    fun registeredPreprocessorsWithPriority(): List<Pair<CodePreprocessor, Int>>
}
