package org.jetbrains.kotlin.jupyter.test

import org.jetbrains.kotlin.jupyter.common.ReplLineMagic
import org.jetbrains.kotlin.jupyter.magics.AbstractMagicsHandler
import org.junit.jupiter.api.Test

class MagicsHandlingTest {
    @Test
    fun `abstract handler should handle all kinds of magics`() {
        val handler = object : AbstractMagicsHandler() {}
        ReplLineMagic.values().forEach { magic ->
            // These invocations should not throw
            handler.handle(magic, "", tryIgnoreErrors = false, parseOnly = true)
        }
    }
}
