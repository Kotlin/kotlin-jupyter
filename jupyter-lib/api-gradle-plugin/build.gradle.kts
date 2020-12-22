import org.jetbrains.kotlinx.jupyter.publishing.addPublication

plugins {
    id("org.jlleitschuh.gradle.ktlint")
    `java-gradle-plugin`
    `kotlin-dsl`
    id("org.jetbrains.kotlinx.jupyter.publishing")
}

project.version = rootProject.version

repositories {
    jcenter()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.google.code.gson:gson:2.8.6")
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

tasks.processResources {
    dependsOn(saveVersion)
}

gradlePlugin {
    plugins {
        create("apiGradlePlugin") {
            id = "org.jetbrains.kotlinx.jupyter.api.plugin"
            implementationClass = "org.jetbrains.kotlinx.jupyter.api.plugin.ApiGradlePlugin"
        }
    }
}

addPublication {
    publicationName = "apiGradlePlugin"
    artifactId = "kotlin-jupyter-api-gradle-plugin"
    bintrayDescription = "Gradle plugin providing a smooth Jupyter notebooks integration for Kotlin libraries"
    bintrayPackageName = artifactId
}
