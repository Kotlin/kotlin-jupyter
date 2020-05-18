package org.jetbrains.kotlin.jupyter.repl.reflect

import jupyter.kotlin.KotlinContext
import jupyter.kotlin.KotlinFunctionInfo
import jupyter.kotlin.KotlinVariableInfo
import org.jetbrains.kotlin.jupyter.instances
import org.slf4j.LoggerFactory

import java.lang.reflect.Field
import java.util.*
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.jvm.kotlinProperty
import kotlin.script.experimental.jvm.BasicJvmReplEvaluator
import kotlin.script.experimental.jvm.KJvmEvaluatedSnippet
import kotlin.script.experimental.util.LinkedSnippet

/**
 * ContextUpdater updates current user-defined functions and variables
 * to use in completion and KotlinContext.
 */
class ContextUpdater(val context: KotlinContext, private val evaluator: BasicJvmReplEvaluator) {

    var lastProcessedSnippet: LinkedSnippet<KJvmEvaluatedSnippet>? = null

    fun update() {
        try {
            var lastSnippet = evaluator.lastEvaluatedSnippet
            val newSnippets = mutableListOf<Any>()
            while (lastSnippet != lastProcessedSnippet && lastSnippet != null) {
                val line = lastSnippet.get().result.scriptInstance
                if (line != null)
                    newSnippets.add(line)
                lastSnippet = lastSnippet.previous
            }
            newSnippets.reverse()
            refreshVariables(newSnippets)
            refreshMethods(newSnippets)
            lastProcessedSnippet = evaluator.lastEvaluatedSnippet
        } catch (e: ReflectiveOperationException) {
            logger.error("Exception updating current variables", e)
        } catch (e: NullPointerException) {
            logger.error("Exception updating current variables", e)
        }
    }

    private fun refreshMethods(lines: List<Any>) {
        for (line in lines) {
            val methods = line.javaClass.methods
            for (method in methods) {
                if (objectMethods.contains(method) || method.name == "main") {
                    continue
                }
                val function = method.kotlinFunction ?: continue
                context.functions.put(function.name, KotlinFunctionInfo(function, line))
            }
        }
    }

    @Throws(ReflectiveOperationException::class)
    private fun refreshVariables(lines: List<Any>) {
        for (line in lines) {
            val fields = line.javaClass.declaredFields
            findVariables(fields, line)
        }
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
            if (fieldName.contains("$\$implicitReceiver") || fieldName.contains("script$")) {
                continue
            }

            field.isAccessible = true
            val value = field.get(o)
            val descriptor = field.kotlinProperty
            if (descriptor != null) {
                context.vars.put(fieldName, KotlinVariableInfo(value, descriptor, o))
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ContextUpdater::class.java)
        private val objectMethods = HashSet(listOf(*Any::class.java.methods))
    }
}
