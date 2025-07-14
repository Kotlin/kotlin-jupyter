package org.jetbrains.kotlinx.jupyter.test

import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.magics.CompositeMagicsHandler
import org.jetbrains.kotlinx.jupyter.magics.Slf4jLoggingManager
import org.jetbrains.kotlinx.jupyter.magics.contexts.CommandHandlingMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.CompositeMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.LoggingMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.loadMagicHandlerFactories
import org.junit.jupiter.api.Test

class MagicsHandlingTest {
    @Test
    fun `compound magic handler should handle all kinds of magics`() {
        val context =
            CompositeMagicHandlerContext(
                listOf(
                    LoggingMagicHandlerContext(Slf4jLoggingManager),
                    CommandHandlingMagicHandlerContext(),
                ),
            )

        // Create a registry and register all the handlers
        val magicsHandler =
            CompositeMagicsHandler(context).apply {
                createAndRegister(loadMagicHandlerFactories())
            }

        // These invocations should not throw
        magicsHandler.handle("logLevel info", tryIgnoreErrors = false, parseOnly = false)
        ReplLineMagic.entries.forEach { magic ->
            magicsHandler.handle(magic.nameForUser, tryIgnoreErrors = false, parseOnly = true)
        }
    }
}
