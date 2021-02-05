package org.jetbrains.kotlinx.jupyter.api.plugin.test

import org.jetbrains.kotlinx.jupyter.api.plugin.kotlinJupyterApiVersion
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class UtilTests {

    @Test
    fun testVersion() {
        val version = kotlinJupyterApiVersion().trim()
        assertTrue(version.isNotEmpty())
    }
}
