import build.CreateResourcesTask
import build.util.defaultVersionCatalog
import build.util.devKotlin

plugins {
    id("com.gradle.plugin-publish")
    `java-gradle-plugin`
    `kotlin-dsl`
    id("ru.ileasile.kotlin.publisher")
}

project.group = "org.jetbrains.kotlin"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Temporary solution until Kotlin 1.4 will be supported in
    // .kts buildscripts, and it will be possible to use
    // kotlinx.serialization in plugin code
    implementation(libs.kotlin.gradle.gradle)
    implementation(libs.gson)
    implementation(libs.plugin.ksp)

    testImplementation(projects.api)
    testImplementation(projects.apiAnnotations)
}

CreateResourcesTask.register(project, "saveVersion", tasks.processResources) {
    addSingleValueFile("VERSION", rootSettings.mavenVersion)
    addSingleValueFile("KOTLIN_VERSION", rootProject.defaultVersionCatalog.versions.devKotlin)
}

java {
    withSourcesJar()
    withJavadocJar()
}

buildSettings {
    withLanguageLevel("1.4")
    withTests()
}

val pluginName = "apiGradlePlugin"

gradlePlugin {
    plugins {
        create(pluginName) {
            id = "org.jetbrains.kotlin.jupyter.api"
            implementationClass = "org.jetbrains.kotlinx.jupyter.api.plugin.ApiGradlePlugin"
        }
    }
}

pluginBundle {
    // These settings are set for the whole plugin bundle
    website = rootSettings.projectRepoUrl
    vcsUrl = rootSettings.projectRepoUrl

    (plugins) {
        pluginName {
            // id is captured from java-gradle-plugin configuration
            displayName = "Kotlin Jupyter kernel integration plugin"
            description = "Gradle plugin providing a smooth Jupyter notebooks integration for Kotlin libraries"
            tags = listOf("jupyter", "kernel", "kotlin")
        }
    }

    mavenCoordinates {
        groupId = project.group.toString()
    }
}
