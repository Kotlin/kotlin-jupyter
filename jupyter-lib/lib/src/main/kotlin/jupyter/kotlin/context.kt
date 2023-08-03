package jupyter.kotlin

import java.lang.reflect.Field
import java.util.HashMap
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty

/**
 * Kotlin REPL has built-in context for getting user-declared functions and variables
 * and setting invokeWrapper for additional side effects in evaluation.
 * It can be accessed inside REPL by name `kc`, e.g. kc.showVars()
 */
class KotlinContext(
    private val variables: HashMap<String, KotlinVariableInfo> = HashMap(),
    private val functions: HashMap<String, KotlinFunctionInfo> = HashMap(),
) {
    private val currentCellVariablesNames = mutableSetOf<String>()
    private val currentCellFunctionsNames = mutableSetOf<String>()

    val allFunctions: Map<String, KotlinFunctionInfo> get() = functions
    val allVariables: Map<String, KotlinVariableInfo> get() = variables

    val currentFunctions: Map<String, KotlinFunctionInfo> get() = currentCellFunctionsNames.associateWith { functions[it]!! }
    val currentVariables: Map<String, KotlinVariableInfo> get() = currentCellVariablesNames.associateWith { variables[it]!! }

    fun addVariable(name: String, info: KotlinVariableInfo) {
        variables[name] = info
        currentCellVariablesNames.add(name)
    }

    fun addFunction(name: String, info: KotlinFunctionInfo) {
        functions[name] = info
        currentCellFunctionsNames.add(name)
    }

    fun cellExecutionFinished() {
        currentCellVariablesNames.clear()
        currentCellFunctionsNames.clear()
    }
}

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

class KotlinVariableInfo(
    val value: Any?,
    val kotlinProperty: KProperty<*>?,
    val javaField: Field,
    val line: Any,
) {
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

val KotlinVariableInfo.name: String
    get() = kotlinProperty?.name ?: javaField.name

@Suppress("MemberVisibilityCanBePrivate")
val KotlinVariableInfo.type: String
    get() = kotlinProperty?.returnType?.toString() ?: javaField.genericType.typeName
