import build.excludeKotlinDependencies

plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

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

buildSettings {
    withLanguageLevel("1.4")
    withTests()
    withCompilerArgs {
        requiresOptIn()
    }
}

kotlinPublications {
    publication {
        publicationName.set("api")
        description.set("API for libraries supporting Kotlin Jupyter notebooks")
    }
}
