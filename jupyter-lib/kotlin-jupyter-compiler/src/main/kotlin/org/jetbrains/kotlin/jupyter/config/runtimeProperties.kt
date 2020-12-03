package org.jetbrains.kotlin.jupyter.config

fun String.parseIniConfig() =
    lineSequence().map { it.split('=') }.filter { it.count() == 2 }.map { it[0] to it[1] }.toMap()

fun readResourceAsIniFile(fileName: String) =
    ClassLoader.getSystemResource(fileName)?.readText()?.parseIniConfig().orEmpty()
