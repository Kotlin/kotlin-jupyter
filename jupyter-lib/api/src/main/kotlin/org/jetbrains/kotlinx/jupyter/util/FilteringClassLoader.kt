package org.jetbrains.kotlinx.jupyter.util

class FilteringClassLoader(parent: ClassLoader, val includeFilter: (String) -> Boolean) :
    ClassLoader(parent) {
    override fun loadClass(
        name: String?,
        resolve: Boolean,
    ): Class<*> {
        val c =
            if (name != null && includeFilter(name)) {
                parent.loadClass(name)
            } else {
                parent.parent.loadClass(name)
            }
        if (resolve) {
            resolveClass(c)
        }
        return c
    }
}
