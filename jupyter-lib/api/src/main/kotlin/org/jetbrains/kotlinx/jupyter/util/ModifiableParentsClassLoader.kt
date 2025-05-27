package org.jetbrains.kotlinx.jupyter.util

/**
 * A ClassLoader that supports dynamic addition of parent classloaders at runtime.
 *
 * Key features:
 * - Initializes without a parent classloader
 * - Allows adding multiple parent classloaders dynamically
 * - Designed for custom class resolution strategies
 *
 * Primary usage:
 * 1. Implement and return in [org.jetbrains.kotlinx.jupyter.api.KernelRunMode.createIntermediaryClassLoader]
 * 2. Access via [org.jetbrains.kotlinx.jupyter.api.Notebook.intermediateClassLoader]
 * 3. Cast to [ModifiableParentsClassLoader] and add required parent classloaders as needed
 *
 * Typical use case: Loading IntelliJ plugins' classes in embedded mode.
 */

abstract class ModifiableParentsClassLoader : ClassLoader(null) {
    abstract fun addParent(parent: ClassLoader)
}
