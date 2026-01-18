package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.iKotlinClass
import org.jetbrains.kotlinx.jupyter.protocol.api.logger
import org.jetbrains.kotlinx.jupyter.startup.MAIN_CLASS_NAME
import org.junit.jupiter.api.Test

class ConfigTest {
    @Test
    fun testBranch() {
        val branch = defaultRuntimeProperties.currentBranch
        LOG.debug("Runtime git branch is: $branch")

        val developers =
            listOf(
                "ileasile",
                "nikolay-egorov",
                "ark",
                "cm",
            )

        val pullRegex = "(pull/[1-9]\\d*)"
        val mergeRegex = "(refs/merge/.*)"
        val developersRegex = developers.joinToString("|", "(", ")") + "/.*"

        if (!branch.matches(Regex("$pullRegex|$mergeRegex|$developersRegex"))) {
            branch.indexOf('/').shouldBe(-1)
        }

        branch.isNotBlank().shouldBeTrue()

        val commit = defaultRuntimeProperties.currentSha
        commit.shouldHaveLength(40)
    }

    @Test
    fun testVersion() {
        val version = defaultRuntimeProperties.version
        LOG.debug("Runtime version is: {}", version)

        version.shouldNotBeNull()
    }

    @Test
    fun testVersionRuntimeHelpers() {
        val minExpectedVersion = 11
        val maxExpectedVersion = 21

        (JavaRuntime.javaVersion.versionInteger >= minExpectedVersion).shouldBeTrue()
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
        fullVersion.toString() shouldBe "0.8.12.100500.dev2"

        val fullSnapshotVersion = KotlinKernelVersion.from(major, minor, micro, build, dev, isSnapshot = true)
        fullSnapshotVersion.toString() shouldBe "0.8.12.100500.dev2+SNAPSHOT"
        fullSnapshotVersion?.toMavenVersion() shouldBe "0.8.12-100500-2-SNAPSHOT"

        val releaseVersion = KotlinKernelVersion.from("0.8.12")
        val stableVersion = KotlinKernelVersion.from("0.8.12.100500")
        val devVersion = KotlinKernelVersion.from("0.8.12.100500.dev2")
        val snapshotVersion = KotlinKernelVersion.from("0.8.12.100500.dev2+SNAPSHOT")
        val snapshotVersionMaven = KotlinKernelVersion.fromMavenVersion("0.8.12-SNAPSHOT")
        val snapshotVersionPython = KotlinKernelVersion.from("0.8.12+SNAPSHOT")

        fullVersion.shouldNotBeNull()
        releaseVersion.shouldNotBeNull()
        stableVersion.shouldNotBeNull()
        devVersion.shouldNotBeNull()
        snapshotVersion.shouldNotBeNull()
        snapshotVersionMaven.shouldNotBeNull()
        snapshotVersionPython.shouldNotBeNull()

        snapshotVersion.isSnapshot.shouldBeTrue()
        snapshotVersionMaven.isSnapshot.shouldBeTrue()
        snapshotVersionPython.isSnapshot.shouldBeTrue()

        for (ver in listOf(fullVersion, releaseVersion, stableVersion, devVersion)) {
            ver.shouldNotBeNull()
            ver.major shouldBe major
            ver.minor shouldBe minor
            ver.micro shouldBe micro
        }

        for (ver in listOf(fullVersion, stableVersion, devVersion)) {
            ver.shouldNotBeNull()
            ver.build shouldBe build
        }
        releaseVersion.build.shouldBeNull()

        for (ver in listOf(fullVersion, devVersion)) {
            ver.dev shouldBe dev
        }
        releaseVersion.dev.shouldBeNull()
        stableVersion.dev.shouldBeNull()

        releaseVersion shouldBeLessThan stableVersion
        stableVersion shouldBeLessThan devVersion
        fullVersion shouldBe devVersion
        snapshotVersion shouldBeLessThan devVersion
        snapshotVersionMaven shouldBeLessThan releaseVersion
        snapshotVersionPython shouldBeLessThan releaseVersion

        KotlinKernelVersion.from("0.12.0.1+SNAPSHOT")!! shouldBeLessThan
            KotlinKernelVersion.from("0.12.0.1")!!

        val snapshotFromPyPiVersion = KotlinKernelVersion.from("0.12.0+SNAPSHOT")!!
        snapshotFromPyPiVersion shouldBeLessThan KotlinKernelVersion.from("0.12.0")!!

        val snapshotFromMavenVersion = KotlinKernelVersion.fromMavenVersion("0.12.0-SNAPSHOT")!!
        snapshotFromPyPiVersion shouldBe snapshotFromMavenVersion
        snapshotFromPyPiVersion.hashCode() shouldBe snapshotFromMavenVersion.hashCode()

        KotlinKernelVersion.from("5.0.2")!! shouldBeGreaterThan
            KotlinKernelVersion.from("5.0.1.999")!!

        KotlinKernelVersion.from("0.-1.2").shouldBeNull()
        KotlinKernelVersion.from("5.1.2.3.4").shouldBeNull()
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
        val sortedVersions =
            listOf(
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
                "0.12.0",
                "0.12.0-SNAPSHOT",
            ).sortedWith(KotlinKernelVersion.STRING_VERSION_COMPARATOR)

        sortedVersions shouldBe
            listOf(
                "abcdef",
                "yux",
                "0.8.0-1-1",
                "0.8.0-1-2",
                "0.8.0-42",
                "0.10.3.1.dev1",
                "0.10.4.2",
                "0.11.0-2",
                "0.11.0-42",
                "0.12.0-SNAPSHOT",
                "0.12.0",
                "0.12.0-1",
            )
    }

    @Test
    fun `kernel main class name should be consistent`() {
        MAIN_CLASS_NAME shouldBe iKotlinClass.name
    }

    companion object {
        val LOG = testLoggerFactory.logger<ConfigTest>()
    }
}
