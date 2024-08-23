import build.util.compileOnly
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
    compileOnly(libs.logging.slf4j.api)

    api(libs.serialization.json) {
        excludeKotlinDependencies(
            "stdlib",
            "stdlib-common",
        )
    }
}

buildSettings {
    withLanguageVersion(rootSettings.gradleCompatibleKotlinLanguageLevel)
    withApiVersion(rootSettings.gradleCompatibleKotlinLanguageLevel)
    withTests()
    withCompilerArgs {
        requiresOptIn()
        allowResultReturnType()
        jdkRelease(libs.versions.jvmTarget.get())
    }
}

kotlinPublications {
    publication {
        publicationName.set("api")
        description.set("API for libraries supporting Kotlin Jupyter notebooks")
    }
}
