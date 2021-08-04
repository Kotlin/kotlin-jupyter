plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(projects.kotlinJupyterKernel)
    implementation(libs.kotlin.dev.scriptingJvm)
    implementation(libs.test.kotlintest.assertions)
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withTests()
}

kotlinPublications {
    publication {
        publicationName.set("test-kit")
        description.set("Test suite for testing Kotlin kernel library integration")
    }
}
