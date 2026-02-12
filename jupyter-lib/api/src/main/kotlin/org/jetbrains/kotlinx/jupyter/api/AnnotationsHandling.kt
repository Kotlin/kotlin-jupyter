package org.jetbrains.kotlinx.jupyter.api

import kotlin.reflect.KClass

/**
 * Callback to handle new class or interface declarations in executed snippets
 */
typealias ClassDeclarationsCallback = KotlinKernelHost.(List<KClass<*>>) -> Unit

/**
 * Annotation handler used to hook class declarations with specific annotations
 */
class ClassAnnotationHandler(
    val annotation: KClass<out Annotation>,
    val callback: ClassDeclarationsCallback,
)

typealias ExecutionCallback<T> = KotlinKernelHost.() -> T

typealias AfterCellExecutionCallback = KotlinKernelHost.(snippetInstance: Any, result: FieldValue) -> Unit

typealias InterruptionCallback = KotlinKernelHost.() -> Unit
