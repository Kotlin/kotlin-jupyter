package org.jetbrains.kotlinx.jupyter.api.test

import io.kotest.matchers.booleans.shouldBeFalse
import org.jetbrains.kotlinx.jupyter.api.session.JupyterSessionInfo
import org.junit.jupiter.api.Test

class SessionInfoTest {
    @Test
    fun `pure API dependency shouldn't say it's run with kernel`() {
        JupyterSessionInfo.isRunWithKernel().shouldBeFalse()
    }
}
