package org.jetbrains.kotlinx.jupyter.build

import khttp.structures.authorization.BasicAuthorization
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.tooling.BuildException
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

    var updateLibBranchName: String? = null

    val updateLibraryParamTask = tasks.register("updateLibraryParam") {
        doLast {
            val libName = project.property("jupyter.lib.name") as String
            val paramName = project.property("jupyter.lib.param.name") as String
            val paramValue = project.property("jupyter.lib.param.value") as String

            updateLibBranchName = "update-$libName-$paramName-$paramValue"
            updateLibraryParam(libName, paramName, paramValue)
        }
    }

    val pushChangesTask = tasks.register("pushChanges") {
        dependsOn(updateLibraryParamTask)

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
            execGit("push", "--force", "-u", repoUrl, getCurrentBranch() + ":" + updateLibBranchName!!) {
                this.standardOutput = object: OutputStream() {
                    override fun write(b: Int) { }
                }
            }

            execGit("reset", "--hard", "HEAD~")
        }
    }

    tasks.register("makeChangesPR") {
        dependsOn(pushChangesTask)

        doLast {
            val user = rootProject.property("jupyter.github.user") as String
            val password = rootProject.property("jupyter.github.token") as String
            val auth = BasicAuthorization(user, password)
            fun githubRequest(type: String, request: String, json: Map<String, Any>? = null): Int {
                val response = khttp.request(
                    type,
                    "https://api.github.com/$request",
                    json = json,
                    auth = auth
                )
                println(response.text)
                return response.statusCode
            }

            val code = githubRequest("POST", "repos/Kotlin/kotlin-jupyter/pulls", mapOf(
                "title" to "Update library versions",
                "head" to updateLibBranchName!!,
                "base" to "master"
            ))
            if(code != 200 && code != 201) {
                throw BuildException("Creating PR failed with code $code", null)
            }
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
