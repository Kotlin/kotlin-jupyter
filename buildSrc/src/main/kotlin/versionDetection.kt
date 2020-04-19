import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.invoke
import java.nio.file.Path
import java.util.regex.Pattern

fun Project.isProtectedBranch(): Boolean {
    val branchProp = "build.branch"
    var branch = project.findProperty(branchProp) as String?
    println("Current branch: $branch")
    if (branch != null) {
        branch = branch.substring(branch.lastIndexOf("/") + 1)
        return branch == "master"
    }
    return false
}

fun Project.detectVersion(baseVersion: String, artifactsDir: Path, versionFileName: String): String {
    val isOnProtectedBranch by extra(isProtectedBranch())
    val buildCounterStr = rootProject.findProperty("build.counter") as String? ?: "100500"
    val buildNumber = rootProject.findProperty("build.number") as String? ?: ""
    val devCounter = rootProject.findProperty("build.devCounter") as String? ?: "1"
    val devAddition = if(isOnProtectedBranch) "" else ".dev$devCounter"
    val defaultBuildNumber = "$baseVersion.$buildCounterStr$devAddition"
    val buildNumberRegex = "[0-9]+(\\.[0-9]+){3}(\\.dev[0-9]+)?"

    return if (!Pattern.matches(buildNumberRegex, buildNumber)) {
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
