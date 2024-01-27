package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.shouldBe
import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.iKotlinClass
import org.jetbrains.kotlinx.jupyter.log
import org.jetbrains.kotlinx.jupyter.startup.mainClassName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class ConfigTest {
    @Test
    fun testBranch() {
        val branch = defaultRuntimeProperties.currentBranch
        log.debug("Runtime git branch is: $branch")

        if (!branch.matches(Regex("pull/[1-9]\\d*"))) {
            assertEquals(-1, branch.indexOf('/'), "Branch name should be simple")
        }

        assertTrue(branch.isNotBlank(), "Branch name shouldn't be blank")

        val commit = defaultRuntimeProperties.currentSha
        assertEquals(40, commit.length)
    }

    @Test
    fun testVersion() {
        val version = defaultRuntimeProperties.version
        log.debug("Runtime version is: {}", version)

        assertNotNull(version)
    }

    @Test
    fun testVersionRuntimeHelpers() {
        val minExpectedVersion = 6
        val maxExpectedVersion = 20

        assertTrue(JavaRuntime.versionAsInt >= minExpectedVersion)
        JavaRuntime.assertVersion { it >= minExpectedVersion }
        JavaRuntime.assertVersionAtLeast(minExpectedVersion)
        JavaRuntime.assertVersionInRange(minExpectedVersion, maxExpectedVersion)
    }

    @Test
    fun testKernelVersion() {
        val major = 0
        val minor = 8
        val micro = 12
        val build = 100500
        val dev = 2

        val fullVersion = KotlinKernelVersion.from(major, minor, micro, build, dev)
        assertEquals("0.8.12.100500.dev2", fullVersion.toString())

        val releaseVersion = KotlinKernelVersion.from("0.8.12")
        val stableVersion = KotlinKernelVersion.from("0.8.12.100500")
        val devVersion = KotlinKernelVersion.from("0.8.12.100500.dev2")

        assertNotNull(fullVersion)
        assertNotNull(releaseVersion)
        assertNotNull(stableVersion)
        assertNotNull(devVersion)

        for (ver in listOf(fullVersion, releaseVersion, stableVersion, devVersion)) {
            assertEquals(major, ver.major)
            assertEquals(minor, ver.minor)
            assertEquals(micro, ver.micro)
        }

        for (ver in listOf(fullVersion, stableVersion, devVersion)) {
            assertEquals(build, ver.build)
        }
        assertNull(releaseVersion.build)

        for (ver in listOf(fullVersion, devVersion)) {
            assertEquals(dev, ver.dev)
        }
        assertNull(releaseVersion.dev)
        assertNull(stableVersion.dev)

        assertTrue(releaseVersion < stableVersion)
        assertTrue(stableVersion < devVersion)
        assertEquals(fullVersion, devVersion)
        assertTrue(KotlinKernelVersion.from("5.0.2")!! > KotlinKernelVersion.from("5.0.1.999")!!)

        assertNull(KotlinKernelVersion.from("0.-1.2"))
        assertNull(KotlinKernelVersion.from("5.1.2.3.4"))
    }

    @Test
    fun `fromMavenVersion should work correctly`() {
        with(KotlinKernelVersion.Companion) {
            from("0.8.12") shouldBe fromMavenVersion("0.8.12")
            from("0.8.12.100500") shouldBe fromMavenVersion("0.8.12-100500")
            from("0.8.12.100500.dev2") shouldBe fromMavenVersion("0.8.12-100500-2")
        }
    }

    @Test
    fun `maven central versions should be sorted correctly`() {
        val sortedVersions = listOf(
            "0.8.0-1-2",
            "0.12.0-1",
            "yux",
            "0.11.0-42",
            "abcdef",
            "0.11.0-2",
            "0.10.4.2",
            "0.8.0-1-1",
            "0.8.0-42",
            "0.10.3.1.dev1",
        ).sortedWith(KotlinKernelVersion.STRING_VERSION_COMPARATOR)

        sortedVersions shouldBe listOf(
            "abcdef",
            "yux",
            "0.8.0-1-1",
            "0.8.0-1-2",
            "0.8.0-42",
            "0.10.3.1.dev1",
            "0.10.4.2",
            "0.11.0-2",
            "0.11.0-42",
            "0.12.0-1",
        )
    }

    @Test
    fun `kernel main class name should be consistent`() {
        mainClassName shouldBe iKotlinClass.name
    }
}
