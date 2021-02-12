package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.ClassAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.ClassDeclarationsCallback
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.compiler.util.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.compiler.util.rethrowAsLibraryException
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
            .forEach { (annotationClass, classesList) ->
                val handler = handlers[annotationClass.qualifiedName!!]
                if (handler != null) {
                    rethrowAsLibraryException(LibraryProblemPart.CLASS_ANNOTATIONS) {
                        handler(host, classesList.map { it.second })
                    }
                }
            }
    }
}
