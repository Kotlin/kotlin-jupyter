package org.jetbrains.kotlinx.jupyter.api.libraries

/**
 * [VariablesSubstitutionAware] provides interface for variables substitution.
 *
 * It is supposed that [T] implements `VariablesSubstitutionAware<T>`
 *
 * If implementors don't have variables to be replaced, they may return `this`.
 * Non-trivial implementations are supposed for classes representing text code
 * snippets.
 */
interface VariablesSubstitutionAware<out T> {
    /**
     * Replace variables and return the result.
     *
     * @param mapping maps variables names to their values
     * @return instance with substituted variables
     */
    fun replaceVariables(mapping: Map<String, String>): T
}
