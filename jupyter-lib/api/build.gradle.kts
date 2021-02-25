import org.jetbrains.kotlinx.jupyter.build.excludeKotlinDependencies
import org.jetbrains.kotlinx.jupyter.publishing.addPublication

plugins {
    id("org.jetbrains.kotlinx.jupyter.publishing")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

project.version = rootProject.version
val kotlinxSerializationVersion: String by rootProject
val junitVersion: String by rootProject

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("reflect"))

    api("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion") {
        excludeKotlinDependencies(
            "stdlib",
            "stdlib-common"
        )
    }

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
    artifactId = "kotlin-jupyter-api"
    description = "API for libraries supporting Kotlin Jupyter notebooks"
    packageName = artifactId
}
