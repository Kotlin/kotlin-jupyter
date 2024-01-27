package build

import build.util.BUILD_LIBRARIES
import build.util.getPropertyByCommand
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import org.gradle.api.Project
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.tooling.BuildException
import org.jetbrains.kotlinx.jupyter.common.Request
import org.jetbrains.kotlinx.jupyter.common.Response
import org.jetbrains.kotlinx.jupyter.common.ResponseWrapper
import org.jetbrains.kotlinx.jupyter.common.httpRequest
import org.jetbrains.kotlinx.jupyter.common.jsonObject
import org.jetbrains.kotlinx.jupyter.common.successful
import org.jetbrains.kotlinx.jupyter.common.withBasicAuth
import org.jetbrains.kotlinx.jupyter.common.withJson
import java.io.OutputStream

class LibraryUpdateTasksConfigurator(
    private val project: Project,
    private val settings: RootSettingsExtension,
) {
    @Serializable
    class NewPrData(
        @Suppress("unused")
        val title: String,
        @Suppress("unused")
        val head: String,
        @Suppress("unused")
        val base: String,
    )

    @Serializable
    class SetLabelsData(
        @Suppress("unused")
        val labels: List<String>,
    )

    fun registerTasks() {
        var updateLibBranchName: String? = null

        project.tasks.register(UPDATE_LIBRARY_PARAM_TASK) {
            doLast {
                val libName = settings.libName
                val paramName = settings.libParamName
                val paramValue = settings.libParamValue

                updateLibBranchName = "update-$libName-$paramName-$paramValue"
                updateLibraryParam(libName, paramName, paramValue)
            }
        }

        project.tasks.register(PUSH_CHANGES_TASK) {
            dependsOn(UPDATE_LIBRARY_PARAM_TASK)

            fun execGit(vararg args: String, configure: ExecSpec.() -> Unit = {}): ExecResult {
                return project.exec {
                    this.executable = "git"
                    this.args = args.asList()
                    this.workingDir = settings.librariesDir

                    configure()
                }
            }

            doLast {
                execGit("config", "user.email", "robot@jetbrains.com")
                execGit("config", "user.name", "robot")

                execGit("add", ".")
                execGit("commit", "-m", "[AUTO] Update library version")

                val currentBranch = project.getPropertyByCommand(
                    "build.libraries.branch",
                    arrayOf("git", "rev-parse", "--abbrev-ref", "HEAD"),
                    settings.librariesDir,
                )
                execGit("push", "--force", "-u", settings.librariesRepoUrl, "$currentBranch:refs/heads/" + updateLibBranchName!!) {
                    this.standardOutput = object : OutputStream() {
                        override fun write(b: Int) { }
                    }
                }

                execGit("reset", "--hard", "HEAD~")
            }
        }

        project.tasks.register(MAKE_CHANGES_PR_TASK) {
            dependsOn(PUSH_CHANGES_TASK)

            doLast {
                val user = settings.prGithubUser
                val password = settings.prGithubToken
                val repoUserAndName = settings.librariesRepoUserAndName
                fun githubRequest(
                    method: String,
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
                    "POST", "repos/$repoUserAndName/pulls",
                    Json.encodeToJsonElement(
                        NewPrData(
                            title = "Update `${settings.libName}` library to `${settings.libParamValue}`",
                            head = updateLibBranchName!!,
                            base = "master"
                        )
                    )
                ) { response ->
                    throw BuildException("Creating PR failed with code ${response.status.code}", null)
                }

                val prNumber = (prResponse.jsonObject["number"] as JsonPrimitive).int
                githubRequest(
                    "POST", "repos/$repoUserAndName/issues/$prNumber/labels",
                    Json.encodeToJsonElement(
                        SetLabelsData(listOf("no-changelog", "library-descriptors"))
                    )
                ) { response ->
                    throw BuildException("Cannot setup labels for created PR: ${response.text}", null)
                }
            }
        }
    }

    private fun updateLibraryParam(libName: String, paramName: String, paramValue: String) {
        val libFile = project.file(settings.librariesDir).resolve(BUILD_LIBRARIES.descriptorFileName(libName))
        val libText = libFile.readText()
        val paramRegex = Regex("""^([ \t]*"$paramName"[ \t]*:[ \t]*")(.*)("[ \t]*,?)$""", RegexOption.MULTILINE)
        val newText = libText.replace(paramRegex, "$1$paramValue$3")
        libFile.writeText(newText)
    }
}
