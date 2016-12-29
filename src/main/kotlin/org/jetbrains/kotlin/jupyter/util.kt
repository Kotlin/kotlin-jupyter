package org.jetbrains.kotlin.jupyter

import java.io.File
import kotlin.reflect.KClass


fun <T: Any> KClass<T>.containingClasspath(): File? {
    val clp = "${qualifiedName?.replace('.', '/')}.class"
    val url = Thread.currentThread().contextClassLoader.getResource(clp)?.toString() ?: return null
    val filenameRegex = """(?:zip:|jar:file:)(.*)!/(?:.*)""".toRegex()
    val matched = filenameRegex.find(url) ?: throw IllegalStateException("Expecting a local classpath when searching for class: ${qualifiedName}")
    val pathToJar = matched.groupValues[1]
    return File(pathToJar)
}