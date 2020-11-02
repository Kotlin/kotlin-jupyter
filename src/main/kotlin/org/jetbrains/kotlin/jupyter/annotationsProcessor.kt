package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.KotlinFunctionInfo
import org.jetbrains.kotlin.jupyter.api.Code
import org.jetbrains.kotlin.jupyter.api.GenerativeTypeHandler
import org.jetbrains.kotlin.jupyter.api.TypeName
import org.jetbrains.kotlin.jupyter.repl.ContextUpdater

interface AnnotationsProcessor {

    fun register(handler: GenerativeTypeHandler): Code

    fun process(line: Any): List<Code>
}

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

    override fun process(line: Any): List<Code> {
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
        line.javaClass.kotlin.nestedClasses.forEach { kClass ->
            kClass.annotations.forEach {
                val annotationType = it.annotationClass.qualifiedName!!
                val handler = handlers[annotationType]
                if (handler != null) {
                    val result = handler.function.call(handler.line, it, kClass)
                    (result as? List<String>?)?.let(codeToExecute::addAll)
                }
            }
        }
        return codeToExecute
    }
}
