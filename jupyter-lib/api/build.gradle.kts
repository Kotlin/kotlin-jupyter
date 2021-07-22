import build.excludeKotlinDependencies
import build.withCompilerArgs
import build.withLanguageLevel
import build.withTests

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
}

withLanguageLevel("1.4")
withTests()
withCompilerArgs {
    requiresOptIn()
}

kotlinPublications {
    publication {
        publicationName = "api"
        artifactId = "kotlin-jupyter-api"
        description = "API for libraries supporting Kotlin Jupyter notebooks"
        packageName = artifactId
    }
}
