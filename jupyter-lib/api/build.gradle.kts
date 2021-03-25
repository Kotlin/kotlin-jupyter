import org.jetbrains.kotlinx.jupyter.build.excludeKotlinDependencies

plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

project.version = rootProject.version
val kotlinxSerializationVersion: String by rootProject
val stableKotlinVersion: String by rootProject
val junitVersion: String by rootProject

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(kotlin("stdlib", stableKotlinVersion))
    compileOnly(kotlin("reflect", stableKotlinVersion))

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

kotlinPublications {
    publication {
        publicationName = "api"
        artifactId = "kotlin-jupyter-api"
        description = "API for libraries supporting Kotlin Jupyter notebooks"
        packageName = artifactId
    }
}
