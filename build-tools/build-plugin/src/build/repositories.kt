package build

import org.gradle.api.Project
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories
import java.io.File

fun Project.addAllBuildRepositories() {
    val kotlinVersion = rootProject.defaultVersionCatalog.versions.devKotlin

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()

        // Kotlin Dev releases are published here every night
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")

        class TeamcitySettings(
            val url: String,
            val projectId: String
        )
        val teamcityRepos = listOf(
            TeamcitySettings("https://teamcity.jetbrains.com", "Kotlin_KotlinPublic_Artifacts"),
            TeamcitySettings("https://buildserver.labs.intellij.net", "Kotlin_KotlinDev_Artifacts")
        )
        for (teamcity in teamcityRepos) {
            maven("${teamcity.url}/guestAuth/app/rest/builds/buildType:(id:${teamcity.projectId}),number:$kotlinVersion,branch:default:any/artifacts/content/maven")
        }

        // Used for TeamCity build
        val m2LocalPath = File(".m2/repository")
        if (m2LocalPath.exists()) {
            maven(m2LocalPath.toURI())
        }
    }
}
