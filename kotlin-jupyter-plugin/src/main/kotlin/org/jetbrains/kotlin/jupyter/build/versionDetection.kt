package org.jetbrains.kotlin.jupyter.build

import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.invoke
import java.io.ByteArrayOutputStream
import java.nio.file.Path

fun Project.getPropertyByCommand(propName: String, cmdArgs: Array<String>): String {
    val prop = project.findProperty(propName) as String?

    if (prop != null) {
        return prop
    }

    val outputStream = ByteArrayOutputStream()
    val result = exec {
        commandLine(*cmdArgs)
        standardOutput = outputStream
    }

    val output = outputStream.toString()
    if (result.exitValue != 0) {
        throw RuntimeException("Unable to get property '$propName'!")
    }

    return output.lines()[0]
}

fun Project.getCurrentBranch(): String =
    // Just result caching, don't set this property explicitly
    project.getOrInitProperty("git.currentBranch") {
        getPropertyByCommand(
                "build.branch",
                arrayOf("git", "rev-parse", "--abbrev-ref", "HEAD")
        )
    }

fun Project.getCurrentCommitSha(): String =
    // Just result caching, don't set this property explicitly
    project.getOrInitProperty("git.currentSha") {
        getPropertyByCommand(
                "build.commit_sha",
                arrayOf("git", "rev-parse", "HEAD")
        )
    }

fun Project.isProtectedBranch(): Boolean {
    val branch = getCurrentBranch()
    println("Current branch: $branch")

    return branch.substring(branch.lastIndexOf("/") + 1) == "master"
}

fun Project.detectVersion(baseVersion: String, artifactsDir: Path, versionFileName: String): String {
    val isOnProtectedBranch by extra(isProtectedBranch())
    val buildCounterStr = rootProject.findProperty("build.counter") as String? ?: "100500"
    val buildNumber = rootProject.findProperty("build.number") as String? ?: ""

    val devCounterOrNull = rootProject.findProperty("build.devCounter") as String?
    val devCounter = devCounterOrNull ?: "1"
    val devAddition = if(isOnProtectedBranch && devCounterOrNull == null) "" else ".dev$devCounter"

    val defaultBuildNumber = "$baseVersion.$buildCounterStr$devAddition"
    val buildNumberRegex = """\d+(\.\d+){3}(\.dev\d+)?"""

    return if (!buildNumber.matches(Regex(buildNumberRegex))) {
        val versionFile = artifactsDir.resolve(versionFileName).toFile()
        if (versionFile.exists()) {
            val lines = versionFile.readLines()
            assert(lines.isNotEmpty()) { "There should be at least one line in VERSION file" }
            lines.first().trim()
        } else {
            defaultBuildNumber
        }
    } else {
        buildNumber
    }
}
