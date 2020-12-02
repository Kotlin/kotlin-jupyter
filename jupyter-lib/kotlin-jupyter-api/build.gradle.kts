import org.jetbrains.kotlin.jupyter.publishing.addPublication

plugins {
    id("org.jetbrains.kotlin.jupyter.publishing")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

project.version = rootProject.version
val kotlinxSerializationVersion: String by rootProject
val junitVersion: String by rootProject

val publicationName = "api"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    api("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    testImplementation(kotlin("test"))

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

addPublication {
    publicationName = "api"
    artifactId = "notebook-api"
    bintrayDescription = "API for Kotlin Jupyter notebooks"
    bintrayPackageName = "kotlin-jupyter-api"
}
