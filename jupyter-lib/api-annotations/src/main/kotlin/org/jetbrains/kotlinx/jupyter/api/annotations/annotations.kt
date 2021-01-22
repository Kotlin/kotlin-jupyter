package org.jetbrains.kotlinx.jupyter.api.annotations

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class JupyterLibraryProducer

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class JupyterLibraryDefinition
