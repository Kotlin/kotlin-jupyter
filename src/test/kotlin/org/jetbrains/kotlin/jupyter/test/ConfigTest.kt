package org.jetbrains.kotlin.jupyter.test

import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlin.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlin.jupyter.LibrariesDir
import org.jetbrains.kotlin.jupyter.LibraryPropertiesFile
import org.jetbrains.kotlin.jupyter.defaultRuntimeProperties
import org.jetbrains.kotlin.jupyter.log
import org.jetbrains.kotlin.jupyter.parseIniConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertNotNull

class ConfigTest {
    @Test
    fun testBranch() {
        val branch = defaultRuntimeProperties.currentBranch
        log.debug("Runtime git branch is: $branch")

        if (!branch.matches(Regex("pull/[1-9][0-9]*")))
            assertEquals(-1, branch.indexOf('/'), "Branch name should be simple")

        assertTrue(branch.isNotBlank(), "Branch name shouldn't be blank")

        val commit = defaultRuntimeProperties.currentSha
        assertEquals(40, commit.length)
    }

    @Test
    fun testLibrariesProperties() {
        val format = defaultRuntimeProperties.librariesFormatVersion
        log.debug("Runtime libs format is: $format")

        assertTrue(format in 2..1000)
        val localProperties = File("$LibrariesDir/$LibraryPropertiesFile").readText().parseIniConfig()
        assertEquals(localProperties["formatVersion"], format.toString())
    }

    @Test
    fun testVersion() {
        val version = defaultRuntimeProperties.version
        log.debug("Runtime version is: $version")

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
}
