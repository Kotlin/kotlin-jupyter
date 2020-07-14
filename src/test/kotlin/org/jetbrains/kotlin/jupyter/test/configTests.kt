package org.jetbrains.kotlin.jupyter.test

import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlin.jupyter.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ConfigTest {
    @Test
    fun testBranch() {
        val branch = runtimeProperties.currentBranch
        log.debug("Runtime git branch is: $branch")

        if (!branch.matches(Regex("pull/[1-9][0-9]*")))
            assertEquals(-1, branch.indexOf('/'), "Branch name should be simple")

        assertTrue(branch.isNotBlank(), "Branch name shouldn't be blank")
    }

    @Test
    fun testLibrariesProperties() {
        val format = runtimeProperties.librariesFormatVersion
        log.debug("Runtime libs format is: $format")

        assertTrue(format in 2..1000)
        val localProperties = File("$LibrariesDir/$LibraryPropertiesFile").readText().parseIniConfig()
        assertEquals(localProperties["formatVersion"], format.toString())
    }

    @Test
    fun testVersion() {
        val version = runtimeProperties.version
        log.debug("Runtime version is: $version")

        assertTrue(version.matches(Regex("""\d+(\.\d+){3}(\.dev\d+)?""")))
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
}
