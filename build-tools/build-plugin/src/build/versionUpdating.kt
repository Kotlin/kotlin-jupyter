package build

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import org.gradle.api.Project
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.tooling.BuildException
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.jetbrains.kotlinx.jupyter.common.ResponseWrapper
import org.jetbrains.kotlinx.jupyter.common.httpRequest
import org.jetbrains.kotlinx.jupyter.common.jsonObject
import org.jetbrains.kotlinx.jupyter.common.text
import org.jetbrains.kotlinx.jupyter.common.withBasicAuth
import org.jetbrains.kotlinx.jupyter.common.withJson
import java.io.File
import java.io.OutputStream

private val Project.libName get() = prop<String>("jupyter.lib.name")
private val Project.libParamName get() = prop<String>("jupyter.lib.param.name")
private val Project.libParamValue get() = prop<String>("jupyter.lib.param.value")

private val Project.prGithubUser get() = prop<String>("jupyter.github.user")
private val Project.prGithubToken get() = prop<String>("jupyter.github.token")

@Serializable
class NewPrData(
    val title: String,
    val head: String,
    val base: String,
)

@Serializable
class SetLabelsData(
    val labels: List<String>,
)

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

            val kotlinVersionProp = "kotlin"
            val gradlePropertiesFile = File("gradle/libs.versions.toml")
            val gradleProperties = gradlePropertiesFile.readLines()
            val updatedGradleProperties = gradleProperties.map {
                if (it.startsWith("$kotlinVersionProp = ")) "$kotlinVersionProp = \"$lastBuildNumber\""
                else it
            }
            gradlePropertiesFile.writeText(updatedGradleProperties.joinToString("\n", "", "\n"))
        }
    }

    var updateLibBranchName: String? = null

    val updateLibraryParamTask = tasks.register("updateLibraryParam") {
        doLast {
            val libName = project.libName
            val paramName = project.libParamName
            val paramValue = project.libParamValue

            updateLibBranchName = "update-$libName-$paramName-$paramValue"
            updateLibraryParam(libName, paramName, paramValue)
        }
    }

    val pushChangesTask = tasks.register("pushChanges") {
        dependsOn(updateLibraryParamTask)

        val librariesDir = projectDir.resolve(librariesPath)
        fun execGit(vararg args: String, configure: ExecSpec.() -> Unit = {}): ExecResult {
            return exec {
                this.executable = "git"
                this.args = args.asList()
                this.workingDir = librariesDir

                configure()
            }
        }

        doLast {
            execGit("config", "user.email", "robot@jetbrains.com")
            execGit("config", "user.name", "robot")

            execGit("add", ".")
            execGit("commit", "-m", "[AUTO] Update library version")

            val repoUrl = rootProject.property("librariesRepoUrl") as String
            val currentBranch = getPropertyByCommand(
                "build.libraries.branch",
                arrayOf("git", "rev-parse", "--abbrev-ref", "HEAD"),
                librariesDir,
            )
            execGit("push", "--force", "-u", repoUrl, "$currentBranch:refs/heads/" + updateLibBranchName!!) {
                this.standardOutput = object : OutputStream() {
                    override fun write(b: Int) { }
                }
            }

            execGit("reset", "--hard", "HEAD~")
        }
    }

    tasks.register("makeChangesPR") {
        dependsOn(pushChangesTask)

        doLast {
            val user = rootProject.prGithubUser
            val password = rootProject.prGithubToken
            val repoUserAndName = rootProject.property("librariesRepoUserAndName") as String
            fun githubRequest(
                method: Method,
                request: String,
                json: JsonElement,
                onFailure: (Response) -> Unit,
            ): ResponseWrapper {
                val response = httpRequest(
                    Request(method, "https://api.github.com/$request")
                        .withJson(json)
                        .withBasicAuth(user, password)
                )
                println(response.text)
                if (!response.status.successful) {
                    onFailure(response)
                }
                return response
            }

            val prResponse = githubRequest(
                Method.POST, "repos/$repoUserAndName/pulls",
                Json.encodeToJsonElement(
                    NewPrData(
                        title = "Update `${rootProject.libName}` library to `${rootProject.libParamValue}`",
                        head = updateLibBranchName!!,
                        base = "master"
                    )
                )
            ) { response ->
                throw BuildException("Creating PR failed with code ${response.status.code}", null)
            }

            val prNumber = (prResponse.jsonObject["number"] as JsonPrimitive).int
            githubRequest(
                Method.POST, "repos/$repoUserAndName/issues/$prNumber/labels",
                Json.encodeToJsonElement(
                    SetLabelsData(listOf("no-changelog", "library-descriptors"))
                )
            ) { response ->
                throw BuildException("Cannot setup labels for created PR: ${response.text}", null)
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
