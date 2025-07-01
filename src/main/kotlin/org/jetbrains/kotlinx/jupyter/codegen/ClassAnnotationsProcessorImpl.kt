package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.ClassAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.ClassDeclarationsCallback
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import org.jetbrains.kotlinx.jupyter.exceptions.LibraryProblemPart
import org.jetbrains.kotlinx.jupyter.exceptions.rethrowAsLibraryException
import kotlin.reflect.KClass

class ClassAnnotationsProcessorImpl : ClassAnnotationsProcessor {
    private val handlers = mutableMapOf<String, ClassDeclarationsCallback>()

    override fun register(handler: ClassAnnotationHandler) {
        handlers[handler.annotation.qualifiedName!!] = handler.callback
    }

    /** Retrieves all nested classes of the given class recursively. */
    private fun KClass<*>.allNestedClasses(): List<KClass<*>> =
        buildList {
            fun addAndTraverseChildren(clazz: KClass<*>) {
                add(clazz)
                for (it in clazz.nestedClasses) {
                    addAndTraverseChildren(it)
                }
            }
            addAndTraverseChildren(this@allNestedClasses)
        }

    override fun process(
        executedSnippet: KClass<*>,
        host: KotlinKernelHost,
    ) {
        executedSnippet
            .allNestedClasses()
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
