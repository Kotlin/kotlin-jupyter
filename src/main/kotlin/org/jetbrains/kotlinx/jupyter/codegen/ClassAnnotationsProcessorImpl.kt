package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.ClassAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.ClassDeclarationsCallback
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import kotlin.reflect.KClass

class ClassAnnotationsProcessorImpl : ClassAnnotationsProcessor {

    private val handlers = mutableMapOf<String, ClassDeclarationsCallback>()

    override fun register(handler: ClassAnnotationHandler) {
        handlers[handler.annotation.qualifiedName!!] = handler.callback
    }

    override fun process(executedSnippet: KClass<*>, host: KotlinKernelHost) {
        executedSnippet.nestedClasses
            .flatMap { clazz -> clazz.annotations.map { it.annotationClass to clazz } }
            .groupBy { it.first }
            .forEach {
                val handler = handlers[it.key.qualifiedName!!]
                if (handler != null) {
                    handler(host, it.value.map { it.second })
                }
            }
    }
}
