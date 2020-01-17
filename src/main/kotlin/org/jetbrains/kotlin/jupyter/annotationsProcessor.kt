package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.completion.KotlinFunctionInfo
import org.jetbrains.kotlin.jupyter.repl.reflect.ContextUpdater

interface AnnotationsProcessor {

    fun register(handler: TypeHandler): Code

    fun process(line: Any): List<Code>
}

class AnnotationsProcessorImpl(private val contextUpdater: ContextUpdater) : AnnotationsProcessor {

    private val handlers = mutableMapOf<TypeName, KotlinFunctionInfo>()

    private val methodIdMap = mutableMapOf<TypeName, Int>()

    private var nextGeneratedMethodId = 0

    private fun getMethodName(id: Int) = "___processAnnotation$id"

    override fun register(handler: TypeHandler): Code {
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
            handlers.putAll(methodIdMap.map {
                it.key to contextUpdater.context.functions[getMethodName(it.value)]!!
            })
            methodIdMap.clear()
        }
        val codeToExecute = mutableListOf<Code>()
        line.javaClass.kotlin.nestedClasses.forEach { kclass ->
            kclass.annotations.forEach {
                val annotationType = it.annotationClass.qualifiedName!!
                val handler = handlers[annotationType]
                if (handler != null) {
                    val result = handler.function.call(handler.line, it, kclass)
                    (result as? List<String>?)?.let(codeToExecute::addAll)
                }
            }
        }
        return codeToExecute
    }
}