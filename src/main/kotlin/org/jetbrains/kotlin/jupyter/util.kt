package org.jetbrains.kotlin.jupyter

import java.io.File
import kotlin.reflect.KClass

fun <T: Any> KClass<T>.containingClasspath(): File? {
    val clp = "${qualifiedName?.replace('.', '/')}.class"
    val url = Thread.currentThread().contextClassLoader.getResource(clp)?.toString() ?: return null
    val zipOrJarRegex = """(?:zip:|jar:file:)(.*)!\/(?:.*)""".toRegex()
    val filePathRegex = """(?:file:)(.*)""".toRegex()
    val foundPath = zipOrJarRegex.find(url)?.let { it.groupValues[1] }
            ?: filePathRegex.find(url)?.let { it.groupValues[1].removeSuffix(clp) }
            ?: throw IllegalStateException("Expecting a local classpath when searching for class: ${qualifiedName}")
    return File(foundPath)
}