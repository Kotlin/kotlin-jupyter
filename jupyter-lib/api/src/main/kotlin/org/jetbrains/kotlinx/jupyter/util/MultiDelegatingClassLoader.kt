package org.jetbrains.kotlinx.jupyter.util

import java.io.IOException
import java.net.URL
import java.util.Collections
import java.util.Enumeration

/**
 * A custom class loader that extends [ModifiableParentsClassLoader] and supports multiple parent class loaders.
 * This allows delegation to multiple specified class loaders for loading classes and resources.
 * We try parents in the order they were added to the class loader, so "older" parents have higher
 * resolution priority.
 * That's why if you have main plugin module A and the submodule of this plugin B (so, B depends on A),
 * the classloader of B should be added before the classloader of A.
 */
class MultiDelegatingClassLoader : ModifiableParentsClassLoader() {
    private val parents = mutableListOf<ClassLoader>()

    override fun addParent(parent: ClassLoader) {
        parents.add(parent)
    }

    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> {
        // First, check if a class is already loaded
        val loaded = findLoadedClass(name)
        if (loaded != null) {
            if (resolve) resolveClass(loaded)
            return loaded
        }

        // Attempt to load a class from each parent in order
        for (parent in parents) {
            try {
                val clazz = parent.loadClass(name)
                if (resolve) resolveClass(clazz)
                return clazz
            } catch (_: ClassNotFoundException) {
                // Try next
            }
        }

        // Class isn't found in any parent
        throw ClassNotFoundException("Class $name not found in any delegate classloader")
    }

    // Optional: restrict resources the same way
    override fun getResource(name: String): URL? {
        for (parent in parents) {
            val resource = parent.getResource(name)
            if (resource != null) return resource
        }
        return null
    }

    override fun getResources(name: String): Enumeration<URL?>? {
        val resources =
            parents.flatMap {
                try {
                    it.getResources(name).toList()
                } catch (_: IOException) {
                    emptyList()
                }
            }
        return Collections.enumeration(resources)
    }
}
