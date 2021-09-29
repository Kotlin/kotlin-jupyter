package org.jetbrains.kotlinx.jupyter.api.libraries

interface LibraryCacheable {
    val shouldBeCachedLocally: Boolean get() = true
    val shouldBeCachedInMemory: Boolean get() = true
}
