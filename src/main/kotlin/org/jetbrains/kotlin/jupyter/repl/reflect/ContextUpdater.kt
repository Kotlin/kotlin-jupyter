package org.jetbrains.kotlin.jupyter.repl.reflect

import jupyter.kotlin.completion.KotlinContext
import jupyter.kotlin.completion.KotlinFunctionInfo
import jupyter.kotlin.completion.KotlinVariableInfo
import org.jetbrains.kotlin.cli.common.repl.AggregatedReplStageState
import org.jetbrains.kotlin.cli.common.repl.ReplHistoryRecord
import org.slf4j.LoggerFactory

import java.lang.reflect.Field
import java.util.*
import java.util.stream.Collectors
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.jvm.kotlinProperty

/**
 * ContextUpdater updates current user-defined functions and variables
 * to use in completion and KotlinContext.
 */
class ContextUpdater(private val state: AggregatedReplStageState<*, *>,
                     val context: KotlinContext) {

    fun update() {
        try {
            val lines = state.lines
            refreshVariables(lines)
            refreshMethods(lines)
        } catch (e: ReflectiveOperationException) {
            logger.error("Exception updating current variables", e)
        } catch (e: NullPointerException) {
            logger.error("Exception updating current variables", e)
        }

    }

    private fun refreshMethods(lines: List<Any>) {
        context.functions.clear()
        for (line in lines) {
            val methods = line.javaClass.methods
            for (method in methods) {
                if (objectMethods.contains(method) || method.name == "main") {
                    continue
                }
                val function = method.kotlinFunction ?: continue
                context.functions.putIfAbsent(function.name, KotlinFunctionInfo(function, line))
            }
        }
    }

    @Throws(ReflectiveOperationException::class)
    private fun getImplicitReceiver(script: Any): Any {
        val receiverField = script.javaClass.getDeclaredField("\$\$implicitReceiver0")
        return receiverField.get(script)
    }

    @Throws(ReflectiveOperationException::class)
    private fun refreshVariables(lines: List<Any>) {
        context.vars.clear()
        if (lines.isNotEmpty()) {
            val receiver = getImplicitReceiver(lines[0])
            findReceiverVariables(receiver)
        }
        for (line in lines) {
            findLineVariables(line)
        }
    }

    // For lines, we only want fields from top level class
    @Throws(IllegalAccessException::class)
    private fun findLineVariables(line: Any) {
        val fields = line.javaClass.declaredFields
        findVariables(fields, line)
    }

    // For implicit receiver, we want to also get fields in parent classes
    @Throws(IllegalAccessException::class)
    private fun findReceiverVariables(receiver: Any) {
        val fieldsList = ArrayList<Field>()
        var cl: Class<*>? = receiver.javaClass
        while (cl != null) {
            fieldsList.addAll(listOf(*cl.declaredFields))
            cl = cl.superclass
        }
        findVariables(fieldsList.toTypedArray(), receiver)
    }

    @Throws(IllegalAccessException::class)
    private fun findVariables(fields: Array<Field>, o: Any) {
        for (field in fields) {
            val fieldName = field.name
            if (fieldName.contains("$\$implicitReceiver")) {
                continue
            }

            field.isAccessible = true
            val value = field.get(o)
            if (!fieldName.contains("script$")) {
                val descriptor = field.kotlinProperty
                if (descriptor != null) {
                    context.vars.putIfAbsent(fieldName, KotlinVariableInfo(value, descriptor, o))
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ContextUpdater::class.java)
        private val objectMethods = HashSet(listOf(*Any::class.java.methods))
    }
}
