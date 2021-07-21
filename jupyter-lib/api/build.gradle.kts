import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlinx.jupyter.build.excludeKotlinDependencies

plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

project.version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.kotlin.gradle.stdlib)
    compileOnly(libs.kotlin.gradle.reflect)

    api(libs.serialization.json) {
        excludeKotlinDependencies(
            "stdlib",
            "stdlib-common"
        )
    }

    testImplementation(libs.kotlin.gradle.test)

    testImplementation(libs.test.junit.api)
    testRuntimeOnly(libs.test.junit.engine)
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
