package jupyter.kotlin

import java.util.HashMap
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty

/**
 * Kotlin REPL has built-in context for getting user-declared functions and variables
 * and setting invokeWrapper for additional side effects in evaluation.
 * It can be accessed inside REPL by name `kc`, e.g. kc.showVars()
 */
class KotlinContext(
    val vars: HashMap<String, KotlinVariableInfo> = HashMap(),
    val functions: HashMap<String, KotlinFunctionInfo> = HashMap()
)

private fun functionSignature(function: KFunction<*>) =
    function.toString().replace("Line_\\d+\\.".toRegex(), "")

private fun shortenType(name: String) =
    name.replace("(\\b[_a-zA-Z$][_a-zA-Z0-9$]*\\b\\.)+".toRegex(), "")

class KotlinFunctionInfo(val function: KFunction<*>, val line: Any) : Comparable<KotlinFunctionInfo> {

    val name: String
        get() = function.name

    fun toString(shortenTypes: Boolean): String {
        return if (shortenTypes) {
            shortenType(toString())
        } else toString()
    }

    override fun toString(): String {
        return functionSignature(function)
    }

    override fun compareTo(other: KotlinFunctionInfo): Int {
        return this.toString().compareTo(other.toString())
    }

    override fun hashCode(): Int {
        return this.toString().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return if (other is KotlinFunctionInfo) {
            this.toString() == other.toString()
        } else false
    }
}

class KotlinVariableInfo(val value: Any?, val descriptor: KProperty<*>, val line: Any) {

    val name: String
        get() = descriptor.name

    @Suppress("MemberVisibilityCanBePrivate")
    val type: String
        get() = descriptor.returnType.toString()

    fun toString(shortenTypes: Boolean): String {
        var type: String = type
        if (shortenTypes) {
            type = shortenType(type)
        }
        return "$name: $type = $value"
    }

    override fun toString(): String {
        return toString(false)
    }
}
