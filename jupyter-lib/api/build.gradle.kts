import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlinx.jupyter.build.excludeKotlinDependencies

plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

project.version = rootProject.version
val kotlinxSerializationVersion: String by rootProject
val junitVersion: String by rootProject
val gradleKotlinVersion: String by rootProject

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(kotlin("stdlib", gradleKotlinVersion))
    compileOnly(kotlin("reflect", gradleKotlinVersion))

    api("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion") {
        excludeKotlinDependencies(
            "stdlib",
            "stdlib-common"
        )
    }

    testImplementation(kotlin("test", gradleKotlinVersion))

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        apiVersion = "1.4"
        languageVersion = "1.4"
    }
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

kotlinPublications {
    publication {
        publicationName = "api"
        artifactId = "kotlin-jupyter-api"
        description = "API for libraries supporting Kotlin Jupyter notebooks"
        packageName = artifactId
    }
}
