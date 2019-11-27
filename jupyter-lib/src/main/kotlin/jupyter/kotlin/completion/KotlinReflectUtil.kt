package jupyter.kotlin.completion

import kotlin.reflect.KFunction

/**
 * Util class for pretty-printing Kotlin variables and functions.
 */
object KotlinReflectUtil {
    fun functionSignature(function: KFunction<*>): String {
        return function.toString().replace("Line_\\d+\\.".toRegex(), "")
    }

    fun shorten(name: String): String {
        return name.replace("(\\b[_a-zA-Z$][_a-zA-Z0-9$]*\\b\\.)+".toRegex(), "")
        // kotlin.collections.List<kotlin.Int> -> List<Int>
    }
}
