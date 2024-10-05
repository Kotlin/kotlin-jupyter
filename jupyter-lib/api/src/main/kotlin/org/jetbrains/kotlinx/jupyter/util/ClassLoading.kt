package org.jetbrains.kotlinx.jupyter.util

val kernelFqnPrefixes =
    listOf(
        "kotlin.",
        "org.jetbrains.kotlin.",
        "jupyter.kotlin.",
        "org.jetbrains.kotlinx.jupyter.api.",
        "kotlinx.serialization.",
    )

fun createDefaultDelegatingClassLoader(parent: ClassLoader): ClassLoader {
    val strategy =
        ClassLoadingDelegatingStrategy { classFqn: String, parentLoader: ClassLoader ->
            val shouldLoadFromKernelClassLoader = kernelFqnPrefixes.any { classFqn.startsWith(it) }
            if (shouldLoadFromKernelClassLoader) {
                parentLoader
            } else {
                parentLoader.parent
            }
        }

    return DelegatingClassLoader(parent, strategy)
}
