package org.jetbrains.kotlinx.jupyter.api.plugin.test

import io.kotest.matchers.string.shouldNotBeEmpty
import org.jetbrains.kotlinx.jupyter.api.plugin.util.kernelVersion
import org.jetbrains.kotlinx.jupyter.api.plugin.util.kotlinVersion
import org.junit.jupiter.api.Test

class UtilTests {
    @Test
    fun testVersions() {
        val version = kernelVersion().trim()
        version.shouldNotBeEmpty()

        val kotlinVersion = kotlinVersion().trim()
        kotlinVersion.shouldNotBeEmpty()
    }
}
