package org.jetbrains.kotlinx.jupyter.util

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.api.TypeName
import org.jetbrains.kotlinx.jupyter.api.libraries.VariablesSubstitutionAware

/**
 * Acceptance rule either says it accepts an object or delegates it to some other rule returning null
 */
fun interface AcceptanceRule<T> {
    fun accepts(obj: T): Boolean?
}

/**
 * Acceptance rule that has only two answers: yes/no (depending on the [acceptsFlag]) and "don't know"
 */
interface FlagAcceptanceRule<T> : AcceptanceRule<T> {
    val acceptsFlag: Boolean

    fun appliesTo(obj: T): Boolean

    override fun accepts(obj: T): Boolean? = if (appliesTo(obj)) acceptsFlag else null
}

/**
 * Acceptance rule for type names
 */
class NameAcceptanceRule(
    override val acceptsFlag: Boolean,
    private val appliesPredicate: (TypeName) -> Boolean,
) : FlagAcceptanceRule<TypeName> {
    override fun appliesTo(obj: TypeName): Boolean = appliesPredicate(obj)
}

/**
 * Acceptance rule for type names based on [pattern].
 * Pattern may consist of any characters and of 3 special combinations:
 * 1) `?` - any single character or no character
 * 2) `*` - any character sequence excluding dot (`.`)
 * 3) `**` - any character sequence
 *
 * For example, pattern `org.jetbrains.kotlin?.**.jupyter.*` matches following names:
 * - `org.jetbrains.kotlin.my.package.jupyter.Integration`
 * - `org.jetbrains.kotlinx.some_package.jupyter.SomeClass`
 *
 * It doesn't match name `org.jetbrains.kotlin.my.package.jupyter.integration.MyClass`
 */
@Serializable(PatternNameAcceptanceRuleSerializer::class)
class PatternNameAcceptanceRule(
    override val acceptsFlag: Boolean,
    val pattern: String,
) : FlagAcceptanceRule<TypeName>,
    VariablesSubstitutionAware<PatternNameAcceptanceRule> {
    private val regex by lazy {
        buildString {
            var i = 0
            while (i < pattern.length) {
                val c = pattern[i]
                val nextC = pattern.getOrNull(i + 1)

                when (c) {
                    '.' -> append("\\.")
                    '*' ->
                        when (nextC) {
                            '*' -> {
                                append(".*")
                                ++i
                            }
                            else -> append("[^.]*")
                        }
                    '?' -> append(".?")
                    '[', ']', '(', ')', '{', '}', '\\', '$', '^', '+', '|' -> {
                        append('\\')
                        append(c)
                    }
                    else -> append(c)
                }
                ++i
            }
        }.toRegex()
    }

    override fun appliesTo(obj: TypeName): Boolean = regex.matches(obj)

    override fun replaceVariables(mapping: Map<String, String>): PatternNameAcceptanceRule {
        val newPattern = replaceVariables(pattern, mapping)
        if (pattern == newPattern) return this
        return PatternNameAcceptanceRule(acceptsFlag, newPattern)
    }
}

/**
 * List of acceptance rules:
 * 1) accepts [obj] if latest not-null acceptance result is `true`
 * 2) doesn't accept [obj] if latest not-null acceptance result is `false`
 * 3) returns `null` if all acceptance results are `null` or the iterable is empty
 */
fun <T> Iterable<AcceptanceRule<T>>.accepts(obj: T): Boolean? = unionAcceptance(map { it.accepts(obj) })

fun unionAcceptance(results: Iterable<Boolean?>): Boolean? = results.filterNotNull().lastOrNull()

fun unionAcceptance(vararg result: Boolean?): Boolean? = unionAcceptance(result.toList())
