package org.jetbrains.kotlinx.jupyter.test

import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.magics.LibrariesAwareAbstractMagicsHandler
import org.junit.jupiter.api.Test

class MagicsHandlingTest {
    @Test
    fun `abstract handler should handle all kinds of magics`() {
        val handler = object : LibrariesAwareAbstractMagicsHandler() {}
        ReplLineMagic.values().forEach { magic ->
            // These invocations should not throw
            handler.handle(magic, "", tryIgnoreErrors = false, parseOnly = true)
        }
    }
}
