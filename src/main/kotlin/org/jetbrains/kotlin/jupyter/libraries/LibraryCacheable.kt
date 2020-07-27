package org.jetbrains.kotlin.jupyter.libraries

interface LibraryCacheable {
    val shouldBeCachedLocally: Boolean get() = true
    val shouldBeCachedInMemory: Boolean get() = true
}
