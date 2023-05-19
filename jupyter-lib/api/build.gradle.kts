import build.util.excludeKotlinDependencies

plugins {
    kotlin("libs.publisher")
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
            "stdlib-common",
        )
    }
}

buildSettings {
    withLanguageVersion("1.6")
    withApiVersion("1.6")
    withTests()
    withCompilerArgs {
        requiresOptIn()
        allowResultReturnType()
    }
}

kotlinPublications {
    publication {
        publicationName.set("api")
        description.set("API for libraries supporting Kotlin Jupyter notebooks")
    }
}
