plugins {
    kotlin("jvm")
    kotlin("jupyter.api")
    id("ru.ileasile.kotlin.publisher")
}

project.version = rootProject.version

val http4kVersion: String by rootProject
val junitVersion: String by rootProject
val stableKotlinVersion: String by rootProject

dependencies {
    implementation(kotlin("stdlib", stableKotlinVersion))
    implementation(kotlin("reflect", stableKotlinVersion))

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

    implementation("org.scilab.forge:jlatexmath:1.0.7")
    implementation("org.apache.xmlgraphics:fop:2.6")
    implementation("org.apache.xmlgraphics:batik-codec:1.14")
    implementation("org.apache.xmlgraphics:xmlgraphics-commons:2.6")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

kotlinPublications {
    publication {
        publicationName = "lib-ext"
        artifactId = "kotlin-jupyter-lib-ext"
        description = "Extended functionality for Kotlin kernel"
        packageName = artifactId
    }
}
