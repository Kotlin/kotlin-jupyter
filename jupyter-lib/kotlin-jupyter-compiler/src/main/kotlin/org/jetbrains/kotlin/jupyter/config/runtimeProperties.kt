package org.jetbrains.kotlin.jupyter.config

fun String.parseIniConfig() =
    lineSequence().map { it.split('=') }.filter { it.count() == 2 }.map { it[0] to it[1] }.toMap()

fun readResourceAsIniFile(fileName: String) =
    readResourceAsIniFile(fileName, ClassLoader.getSystemClassLoader())

fun readResourceAsIniFile(fileName: String, classLoader: ClassLoader) =
    classLoader.getResource(fileName)?.readText()?.parseIniConfig().orEmpty()
