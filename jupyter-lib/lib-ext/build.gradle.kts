import org.jetbrains.kotlinx.jupyter.publishing.addPublication

plugins {
    kotlin("jvm")
    kotlin("jupyter.api")
    id("org.jetbrains.kotlinx.jupyter.publishing")
}

project.version = rootProject.version

val http4kVersion: String by rootProject
val junitVersion: String by rootProject

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    compileOnly(project(":api"))
    implementation(project(":api-annotations"))
    kapt(project(":api-annotations"))

    testImplementation(kotlin("test"))

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    testImplementation(project(":api"))

    fun http4k(name: String) = implementation("org.http4k:http4k-$name:$http4kVersion")
    http4k("core")
    http4k("client-apache")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

addPublication {
    publicationName = "lib-ext"
    artifactId = "kotlin-jupyter-lib-ext"
    description = "Extended functionality for Kotlin kernel"
    packageName = artifactId
}
