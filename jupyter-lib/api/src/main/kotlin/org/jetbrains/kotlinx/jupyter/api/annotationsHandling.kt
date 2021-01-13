package org.jetbrains.kotlinx.jupyter.api

import kotlin.reflect.KClass

/**
 * Callback to handle new class or interface declarations in executed snippets
 */
typealias ClassDeclarationsCallback = KotlinKernelHost.(List<KClass<*>>) -> Unit

/**
 * Annotation handler used to hook class declarations with specific annotations
 */
class AnnotationHandler(val annotation: KClass<out Annotation>, val callback: ClassDeclarationsCallback)
