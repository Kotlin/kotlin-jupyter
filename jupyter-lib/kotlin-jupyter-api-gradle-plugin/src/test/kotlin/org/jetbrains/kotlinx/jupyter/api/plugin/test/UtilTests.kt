package org.jetbrains.kotlinx.jupyter.api.plugin.test

import org.jetbrains.kotlinx.jupyter.api.plugin.util.kernelVersion
import org.jetbrains.kotlinx.jupyter.api.plugin.util.kotlinVersion
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class UtilTests {
    @Test
    fun testVersions() {
        val version = kernelVersion().trim()
        assertTrue(version.isNotEmpty())

        val kotlinVersion = kotlinVersion().trim()
        assertTrue(kotlinVersion.isNotEmpty())
    }
}
