package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.repl.result.Classpath

fun interface ClasspathProvider {
    fun provideClasspath(): Classpath
}
