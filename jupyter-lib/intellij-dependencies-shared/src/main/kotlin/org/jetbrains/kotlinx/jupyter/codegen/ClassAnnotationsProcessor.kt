package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.ClassAnnotationHandler
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost
import kotlin.reflect.KClass

interface ClassAnnotationsProcessor {
    fun register(handler: ClassAnnotationHandler)

    fun process(
        executedSnippet: KClass<*>,
        host: KotlinKernelHost,
    )
}
