package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.AnnotationHandler
import kotlin.reflect.KClass

interface AnnotationsProcessor {

    fun register(handler: AnnotationHandler)

    fun process(executedSnippet: KClass<*>)
}
