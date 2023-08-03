package org.jetbrains.kotlinx.jupyter.repl

import jupyter.kotlin.KotlinContext
import jupyter.kotlin.KotlinFunctionInfo
import jupyter.kotlin.KotlinVariableInfo
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.util.HashSet
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

    private var lastProcessedSnippet: LinkedSnippet<KJvmEvaluatedSnippet>? = null

    fun update() {
        try {
            var lastSnippet = evaluator.lastEvaluatedSnippet
            val newSnippets = mutableListOf<Any>()
            while (lastSnippet != lastProcessedSnippet && lastSnippet != null) {
                val line = lastSnippet.get().result.scriptInstance
                if (line != null) {
                    newSnippets.add(line)
                }
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
                context.addFunction(function.name, KotlinFunctionInfo(function, line))
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

    @Throws(IllegalAccessException::class)
    private fun findVariables(fields: Array<Field>, o: Any) {
        for (field in fields) {
            val fieldName = field.name
            if (fieldName.contains("$\$implicitReceiver") || fieldName.contains("script$")) {
                continue
            }

            field.isAccessible = true
            val value = field.get(o)
            context.addVariable(fieldName, KotlinVariableInfo(value, field.kotlinProperty, field, o))
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ContextUpdater::class.java)
        private val objectMethods = HashSet(listOf(*Any::class.java.methods))
    }
}
