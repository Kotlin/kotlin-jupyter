package org.jetbrains.kotlinx.jupyter.libraries

import java.io.IOException

fun loadResourceFromClassLoader(path: String, classLoader: ClassLoader): String {
    val resource = classLoader.getResource(path)
    resource != null && return resource.readText()
    throw IOException("Resource $path not found on classpath")
}
