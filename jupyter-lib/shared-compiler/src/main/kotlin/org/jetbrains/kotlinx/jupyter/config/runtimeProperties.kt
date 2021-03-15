package org.jetbrains.kotlinx.jupyter.config

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion

fun String.parseIniConfig() =
    lineSequence().map { it.split('=') }.filter { it.count() == 2 }.map { it[0] to it[1] }.toMap()

fun readResourceAsIniFile(fileName: String) =
    readResourceAsIniFile(fileName, ClassLoader.getSystemClassLoader())

fun readResourceAsIniFile(fileName: String, classLoader: ClassLoader) =
    classLoader.getResource(fileName)?.readText()?.parseIniConfig().orEmpty()

private val runtimeProperties by lazy {
    readResourceAsIniFile("compiler.properties", KernelStreams::class.java.classLoader)
}

val currentKernelVersion by lazy {
    KotlinKernelVersion.from(
        runtimeProperties["version"] ?: error("Compiler artifact should contain version")
    )!!
}
