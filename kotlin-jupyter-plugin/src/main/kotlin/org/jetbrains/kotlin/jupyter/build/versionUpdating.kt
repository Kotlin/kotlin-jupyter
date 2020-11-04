package org.jetbrains.kotlin.jupyter.build

import org.gradle.api.Project
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

fun Project.prepareKotlinVersionUpdateTask() {
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
}
