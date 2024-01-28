package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.kotlinx.jupyter.repl.creating.ReplFactoryBase
import org.jetbrains.kotlinx.jupyter.repl.creating.loadDefaultReplFactory
import org.jetbrains.kotlinx.jupyter.test.ReplComponentsProviderMock
import org.junit.jupiter.api.Test

class ReplFactorySpiTest {
    @Test
    fun `base REPL factory should be available on classpath`() {
        val replFactory = loadDefaultReplFactory(ReplComponentsProviderMock)
        replFactory.shouldBeInstanceOf<ReplFactoryBase>()
    }
}
