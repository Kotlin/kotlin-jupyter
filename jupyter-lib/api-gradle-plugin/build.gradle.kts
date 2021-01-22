import org.jetbrains.kotlinx.jupyter.publishing.addPublication

plugins {
    id("org.jlleitschuh.gradle.ktlint")
    `java-gradle-plugin`
    `kotlin-dsl`
    id("org.jetbrains.kotlinx.jupyter.publishing")
}

project.version = rootProject.version

val junitVersion: String by rootProject

repositories {
    jcenter()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Temporary solution until Kotlin 1.4 will be supported in
    // .kts buildscripts and it will be possible to use
    // kotlinx.serialization in plugin code
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.20")
    implementation("com.google.code.gson:gson:2.8.6")

    testImplementation(kotlin("test"))

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    testImplementation(project(":api"))
    testImplementation(project(":api-annotations"))
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

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
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
