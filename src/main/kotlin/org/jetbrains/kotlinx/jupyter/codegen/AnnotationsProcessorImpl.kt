package org.jetbrains.kotlinx.jupyter.codegen

import jupyter.kotlin.KotlinFunctionInfo
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.GenerativeTypeHandler
import org.jetbrains.kotlinx.jupyter.api.TypeName
import org.jetbrains.kotlinx.jupyter.repl.ContextUpdater
import kotlin.reflect.KClass

class AnnotationsProcessorImpl(private val contextUpdater: ContextUpdater) : AnnotationsProcessor {

    private val handlers = mutableMapOf<TypeName, KotlinFunctionInfo>()

    private val methodIdMap = mutableMapOf<TypeName, Int>()

    private var nextGeneratedMethodId = 0

    private fun getMethodName(id: Int) = "___processAnnotation$id"

    override fun register(handler: GenerativeTypeHandler): Code {
        val annotationArgument = "__annotation"
        val classArgument = "__class"
        val body = handler.code
            .replace("\$annotation", annotationArgument)
            .replace("\$kclass", classArgument)
        val annotationType = handler.className
        val methodId = nextGeneratedMethodId++
        val methodName = getMethodName(methodId)
        methodIdMap[annotationType] = methodId
        return "fun $methodName($annotationArgument : $annotationType, $classArgument : kotlin.reflect.KClass<*>) = $body"
    }

    override fun process(kClass: KClass<*>): Code? {
        if (methodIdMap.isNotEmpty()) {
            contextUpdater.update()
            val resolvedMethods = methodIdMap.map {
                it.key to contextUpdater.context.functions[getMethodName(it.value)]
            }.filter { it.second != null }.map { it.first to it.second!! }
            handlers.putAll(resolvedMethods)
            resolvedMethods.forEach {
                methodIdMap.remove(it.first)
            }
        }
        val codeToExecute = mutableListOf<Code>()
        kClass.nestedClasses.forEach { nestedClass ->
            nestedClass.annotations.forEach {
                val annotationType = it.annotationClass.qualifiedName!!
                val handler = handlers[annotationType]
                if (handler != null) {
                    val result = handler.function.call(handler.line, it, nestedClass)
                    (result as? Code)?.let(codeToExecute::add)
                }
            }
        }
        return if(codeToExecute.isEmpty()) return null else codeToExecute.joinToString("\n")
    }
}
