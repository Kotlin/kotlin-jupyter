package build.util

import org.gradle.api.Project
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

    val execTask = providers.exec {
        commandLine(*cmdArgs)
        isIgnoreExitValue = true
        workingDir?.let { this.workingDir = it }
    }

    val result = execTask.result.get()

    val output = execTask.standardOutput.asText.get()
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
