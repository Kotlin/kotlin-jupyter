package org.jetbrains.kotlinx.jupyter.build

import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream

fun ProjectWithInstallOptions.prepareKotlinVersionUpdateTasks() {
    tasks.register("updateKotlinVersion") {
        doLast {
            val teamcityUrl = "https://teamcity.jetbrains.com"
            val requestEndpoint = "guestAuth/app/rest/builds"
            val locator = "buildType:(id:Kotlin_KotlinPublic_Aggregate),status:SUCCESS,branch:default:any,count:1"
            val response = khttp.get(
                "$teamcityUrl/$requestEndpoint/?locator=$locator",
                headers = mapOf("accept" to "application/json")
            )
            val builds = response.jsonObject["build"] as JSONArray
            val lastBuild = builds[0] as JSONObject
            val lastBuildNumber = lastBuild["number"]
            println("Last Kotlin dev version: $lastBuildNumber")

            val kotlinVersionProp = "kotlinVersion"
            val gradlePropertiesFile = File("gradle.properties")
            val gradleProperties = gradlePropertiesFile.readLines()
            val updatedGradleProperties = gradleProperties.map {
                if (it.startsWith("$kotlinVersionProp=")) "$kotlinVersionProp=$lastBuildNumber"
                else it
            }
            gradlePropertiesFile.writeText(updatedGradleProperties.joinToString("\n", "", "\n"))
        }
    }

    tasks.register("updateLibraryParam") {
        doLast {
            val libName = project.property("jupyter.lib.name") as String
            val paramName = project.property("jupyter.lib.param.name") as String
            val paramValue = project.property("jupyter.lib.param.value") as String

            updateLibraryParam(libName, paramName, paramValue)
        }
    }

    tasks.register("pushChanges") {
        fun execGit(vararg args: String, configure: ExecSpec.() -> Unit = {}): ExecResult {
            return exec {
                this.executable = "git"
                this.args = args.asList()
                this.workingDir = projectDir

                configure()
            }
        }

        doLast {
            execGit("config", "user.email", "robot@jetbrains.com")
            execGit("config", "user.name", "robot")

            execGit("add", ".")
            execGit("commit", "-m", "[AUTO] Update library version")

            val repoUrl = rootProject.property("pushRepoUrl") as String
            execGit("push", "-u", repoUrl, getCurrentBranch()) {
                this.standardOutput = object: OutputStream() {
                    override fun write(b: Int) { }
                }
            }

            execGit("reset", "--hard", "HEAD~")
        }
    }
}

fun ProjectWithInstallOptions.updateLibraryParam(libName: String, paramName: String, paramValue: String) {
    val libFile = File(librariesPath).resolve("$libName.json")
    val libText = libFile.readText()
    val paramRegex = Regex("""^([ \t]*"$paramName"[ \t]*:[ \t]*")(.*)("[ \t]*,?)$""", RegexOption.MULTILINE)
    val newText = libText.replace(paramRegex, "$1$paramValue$3")
    libFile.writeText(newText)
}
