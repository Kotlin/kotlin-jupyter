import build.withTests

plugins {
    id("com.gradle.plugin-publish")
    id("org.jlleitschuh.gradle.ktlint")
    `java-gradle-plugin`
    `kotlin-dsl`
    id("ru.ileasile.kotlin.publisher")
}

project.version = rootProject.version
project.group = "org.jetbrains.kotlin"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Temporary solution until Kotlin 1.4 will be supported in
    // .kts buildscripts and it will be possible to use
    // kotlinx.serialization in plugin code
    implementation(libs.kotlin.gradle.gradlePlugin)
    implementation(libs.gson)

    testImplementation(projects.api)
    testImplementation(projects.apiAnnotations)
}

val saveVersion by tasks.registering {
    inputs.property("version", version)

    val outputDir = file(project.buildDir.toPath().resolve("resources").resolve("main"))
    outputs.dir(outputDir)

    doLast {
        outputDir.mkdirs()
        val propertiesFile = outputDir.resolve("VERSION")
        propertiesFile.writeText(version.toString())
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

withTests()

tasks {
    processResources {
        dependsOn(saveVersion)
    }
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
    website = "https://github.com/Kotlin/kotlin-jupyter"
    vcsUrl = "https://github.com/Kotlin/kotlin-jupyter"

    (plugins) {
        pluginName {
            // id is captured from java-gradle-plugin configuration
            displayName = "Kotlin Jupyter kernel integration plugin"
            description = "Gradle plugin providing a smooth Jupyter notebooks integration for Kotlin libraries"
            tags = listOf("jupyter", "kernel", "kotlin")
        }
    }

    mavenCoordinates {
        groupId = "org.jetbrains.kotlin"
    }
}
