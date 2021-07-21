@file:Suppress("UnstableApiUsage")

enableFeaturePreview("VERSION_CATALOGS")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    val kotlinVersion: String by settings

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

gradle.projectsLoaded {
    allprojects {
        repositories.addAll(pluginManagement.repositories)
    }
}

includeBuild("kotlin-jupyter-plugin")

libSubproject("common-dependencies")
libSubproject("lib")
libSubproject("api")
libSubproject("api-annotations")
libSubproject("kotlin-jupyter-api-gradle-plugin")
libSubproject("shared-compiler")
libSubproject("lib-ext")

exampleSubproject("getting-started")

fun libSubproject(name: String) = subproject(name, "jupyter-lib/")
fun exampleSubproject(name: String) = subproject(name, "api-examples/")

fun subproject(name: String, parentPath: String) {
    include(name)
    project(":$name").projectDir = file("$parentPath$name").absoluteFile
}
