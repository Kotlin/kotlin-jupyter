package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.AnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.ClassDeclarationsCallback
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import kotlin.reflect.KClass

class AnnotationsProcessorImpl(private val host: KotlinKernelHost) : AnnotationsProcessor {

    private val handlers = mutableMapOf<KClass<out Annotation>, ClassDeclarationsCallback>()

    override fun register(handler: AnnotationHandler) {
        handlers[handler.annotation] = handler.callback
    }

    override fun process(executedSnippet: KClass<*>) {
        executedSnippet.nestedClasses
            .flatMap { clazz -> clazz.annotations.map { it.javaClass.kotlin to clazz } }
            .groupBy { it.first }
            .forEach {
                val handler = handlers[it.key]
                if (handler != null) {
                    handler(it.value.map { it.second }, host)
                }
            }
    }
}
