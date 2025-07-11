package org.jetbrains.kotlinx.jupyter.api.libraries

interface LibraryCacheable {
    val shouldBeCachedInMemory: Boolean get() = true
}
