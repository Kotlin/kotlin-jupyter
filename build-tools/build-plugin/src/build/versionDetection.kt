package build

import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.invoke
import java.io.ByteArrayOutputStream
import java.io.File

fun Project.getPropertyByCommand(
    propName: String,
    cmdArgs: Array<String>,
    workingDir: File? = null,
): String {
    val prop = project.typedProperty<String?>(propName)

    if (prop != null) {
        return prop
    }

    val outputStream = ByteArrayOutputStream()
    val result = exec {
        commandLine(*cmdArgs)
        standardOutput = outputStream
        workingDir?.let { this.workingDir = it }
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

private val buildNumberRegex = Regex("""(?<base>\d+\.\d+\.\d+)\.(?<counter>\d+)(\.dev(?<devCounter>\d+))?""")

fun String.toMavenVersion(): String {
    val match = buildNumberRegex.find(this)!!
    val base = match.groups["base"]!!.value
    val counter = match.groups["counter"]!!.value
    val devCounter = match.groups["devCounter"]?.value
    val devAddition = if (devCounter == null) "" else "-$devCounter"

    return "$base-$counter$devAddition"
}

fun Project.detectVersion(options: KernelBuildExtension): String {
    val isOnProtectedBranch by extra(isProtectedBranch())
    val buildCounterStr = options.buildCounter
    val buildNumber = options.buildNumber

    val devCounterOrNull = options.devCounter
    val devCounter = devCounterOrNull ?: "1"
    val devAddition = if (isOnProtectedBranch && devCounterOrNull == null) "" else ".dev$devCounter"

    val defaultBuildNumber = "${options.baseVersion}.$buildCounterStr$devAddition"

    return if (!buildNumber.matches(buildNumberRegex)) {
        val versionFile = options.artifactsDir.resolve(options.versionFileName)
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
