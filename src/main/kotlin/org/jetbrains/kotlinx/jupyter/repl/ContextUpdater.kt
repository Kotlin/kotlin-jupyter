package org.jetbrains.kotlinx.jupyter.repl

import com.intellij.openapi.diagnostic.thisLogger
import jupyter.kotlin.KotlinContext
import jupyter.kotlin.KotlinFunctionInfo
import jupyter.kotlin.KotlinVariableInfo
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import org.jetbrains.kotlinx.jupyter.repl.impl.KernelReplEvaluator
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.kotlinFunction
import kotlin.script.experimental.jvm.KJvmEvaluatedSnippet
import kotlin.script.experimental.util.LinkedSnippet

/**
 * ContextUpdater updates current user-defined functions and variables
 * to use in completion and KotlinContext.
 *
 * It does this by using reflection on the scriptInstance in order to detect new properties and functions.
 */
class ContextUpdater(
    loggerFactory: KernelLoggerFactory,
    val context: KotlinContext,
    private val evaluator: KernelReplEvaluator,
) {
    private val logger = loggerFactory.getLogger(this::class)
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
                if (
                    objectMethods.contains(method) ||
                    method.name == "main" &&
                    Modifier.isStatic(method.modifiers) ||
                    // K1 Repl entry point for running a snippet.
                    method.name == "$\$eval" // K2 Repl method containing the snippet code.
                ) {
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
            // `.kotlinProperty doesn't work correctly due to the metadata in snippets
            // not working correctly in K2.
            // See https://youtrack.jetbrains.com/issue/KT-75580/K2-Repl-Cannot-access-snippet-properties-using-Kotlin-reflection
            // So we attempt to find both of them at this point.
            val kotlinProperties = line::class.declaredMemberProperties.toList()
            val javaFields = line.javaClass.declaredFields
            findVariables(javaFields, kotlinProperties, line)
        }
    }

    @Throws(IllegalAccessException::class)
    private fun findVariables(
        javaFields: Array<Field>,
        kotlinProperties: List<KProperty1<out Any, *>>,
        scriptInstance: Any,
    ) {
        for (field in javaFields) {
            val fieldName = field.name
            if (
                // ImplicitReceivers injected through ScriptCompilationConfiguration
                fieldName.contains($$$"$$implicitReceiver") ||
                // TODO What is this?
                fieldName.contains("script$") ||
                // K2 REPL reference to the script instance
                fieldName.contains("INSTANCE")
            ) {
                continue
            }

            // `field.isAccessible` doesn't work correctly.
            // So instead we look up the value through the Java Property for now
            try {
                val kotlinProperty = kotlinProperties.firstOrNull { it.name == fieldName } as KProperty1<Any, *>?
                field.isAccessible = true
                val value = field.get(scriptInstance)
                context.addVariable(fieldName, KotlinVariableInfo(value, kotlinProperty, field, scriptInstance))
            } catch (ex: Exception) {
                thisLogger().error("Exception accessing variable: $fieldName", ex)
            }
        }
    }

    companion object {
        private val objectMethods = HashSet(listOf(*Any::class.java.methods))
    }
}
