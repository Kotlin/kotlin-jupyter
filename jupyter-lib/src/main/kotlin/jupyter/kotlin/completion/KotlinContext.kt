package jupyter.kotlin.completion

import java.util.*

/**
 * Kotlin REPL has built-in context for getting user-declared functions and variables
 * and setting invokeWrapper for additional side effects in evaluation.
 * It can be accessed inside REPL by name `kc`, e.g. kc.showVars()
 */
class KotlinContext(val vars: HashMap<String, KotlinVariableInfo> = HashMap(),
                    val functions: MutableSet<KotlinFunctionInfo> = TreeSet()) {

    fun getVarsList(): List<KotlinVariableInfo> {
        return ArrayList(vars.values)
    }

    fun getFunctionsList(): List<KotlinFunctionInfo> {
        return ArrayList(functions)
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
class KotlinReceiver {
    var kc: KotlinContext? = null
}
