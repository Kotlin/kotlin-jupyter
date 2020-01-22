package jupyter.kotlin

import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty

/**
 * Kotlin REPL has built-in context for getting user-declared functions and variables
 * and setting invokeWrapper for additional side effects in evaluation.
 * It can be accessed inside REPL by name `kc`, e.g. kc.showVars()
 */
class KotlinContext(val vars: HashMap<String, KotlinVariableInfo> = HashMap(),
                    val functions: HashMap<String, KotlinFunctionInfo> = HashMap()) {

    fun getVarsList(): List<KotlinVariableInfo> {
        return ArrayList(vars.values)
    }

    fun getFunctionsList(): List<KotlinFunctionInfo> {
        return ArrayList(functions.values)
    }
}



/**
 * The implicit receiver for lines in Kotlin REPL.
 * It is passed to the script as an implicit receiver, identical to:
 * with (context) {
 * ...
 * }
 *
 * KotlinReceiver can be inherited from and passed to REPL building properties,
 * so other variables and functions can be accessed inside REPL.
 * By default, it only has KotlinContext.
 * Inherited KotlinReceivers should be in separate java file, they can't be inner or nested.
 */
class KotlinReceiver(val kc: KotlinContext)

fun functionSignature(function: KFunction<*>): String {
    return function.toString().replace("Line_\\d+\\.".toRegex(), "")
}

fun shortenType(name: String): String {
    return name.replace("(\\b[_a-zA-Z$][_a-zA-Z0-9$]*\\b\\.)+".toRegex(), "")
    // kotlin.collections.List<kotlin.Int> -> List<Int>
}

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