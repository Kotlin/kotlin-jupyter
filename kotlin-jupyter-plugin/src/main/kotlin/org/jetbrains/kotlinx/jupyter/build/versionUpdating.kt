package org.jetbrains.kotlinx.jupyter.build

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.tooling.BuildException
import java.io.File
import java.io.OutputStream
import org.http4k.core.Method
import org.http4k.core.Request
import org.jetbrains.kotlinx.jupyter.common.jsonObject
import org.jetbrains.kotlinx.jupyter.common.httpRequest
import org.jetbrains.kotlinx.jupyter.common.text
import org.jetbrains.kotlinx.jupyter.common.withBasicAuth
import org.jetbrains.kotlinx.jupyter.common.withJson

fun ProjectWithInstallOptions.prepareKotlinVersionUpdateTasks() {
    tasks.register("updateKotlinVersion") {
        doLast {
            val teamcityUrl = "https://teamcity.jetbrains.com"
            val requestEndpoint = "guestAuth/app/rest/builds"
            val locator = "buildType:(id:Kotlin_KotlinPublic_Aggregate),status:SUCCESS,branch:default:any,count:1"

            val response = httpRequest(
                Request(Method.GET, "$teamcityUrl/$requestEndpoint/?locator=$locator")
                    .header("accept", "application/json")
            )
            val builds = response.jsonObject["build"] as JsonArray
            val lastBuild = builds[0] as JsonObject
            val lastBuildNumber = (lastBuild["number"] as JsonPrimitive).content
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
            fun githubRequest(method: Method, request: String, json: Map<String, String>? = null): Int {
                val response = httpRequest(Request(method, "https://api.github.com/$request")
                    .withJson(Json.encodeToJsonElement(json))
                    .withBasicAuth(user, password)
                )
                println(response.text)
                return response.status.code
            }

            val code = githubRequest(Method.POST, "repos/Kotlin/kotlin-jupyter/pulls", mapOf(
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
