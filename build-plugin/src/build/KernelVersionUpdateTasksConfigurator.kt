package build

import build.util.PUBLIC_KOTLIN_TEAMCITY
import build.util.TEAMCITY_REQUEST_ENDPOINT
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.gradle.api.Project
import org.http4k.core.Method
import org.http4k.core.Request
import org.jetbrains.kotlinx.jupyter.common.httpRequest
import org.jetbrains.kotlinx.jupyter.common.jsonObject

class KernelVersionUpdateTasksConfigurator(
    private val project: Project,
) {
    fun registerTasks() {
        project.tasks.register(UPDATE_KOTLIN_VERSION_TASK) {
            doLast {
                val teamcityProject = PUBLIC_KOTLIN_TEAMCITY
                val teamcityUrl = teamcityProject.url
                val locator = "buildType:(id:${teamcityProject.projectId}),status:SUCCESS,branch:default:any,count:1"

                val builds = httpRequest(
                    Request(Method.GET, "$teamcityUrl/$TEAMCITY_REQUEST_ENDPOINT/?locator=$locator")
                        .header("accept", "application/json")
                ) { it.jsonObject["build"] as JsonArray }
                val lastBuild = builds[0] as JsonObject
                val lastBuildNumber = (lastBuild["number"] as JsonPrimitive).content
                println("Last Kotlin dev version: $lastBuildNumber")

                val kotlinVersionProp = "kotlin"
                val gradlePropertiesFile = project.projectDir.resolve("gradle/libs.versions.toml")
                val gradleProperties = gradlePropertiesFile.readLines()
                val updatedGradleProperties = gradleProperties.map {
                    if (it.startsWith("$kotlinVersionProp = ")) "$kotlinVersionProp = \"$lastBuildNumber\""
                    else it
                }
                gradlePropertiesFile.writeText(updatedGradleProperties.joinToString("\n", "", "\n"))
            }
        }
    }
}
