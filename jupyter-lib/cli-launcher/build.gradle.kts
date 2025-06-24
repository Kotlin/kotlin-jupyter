plugins {
    kotlin("libs.publisher")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(projects.kotlinJupyterKernel)
    api(projects.wsServer)
    api(libs.logging.slf4j.api)
    implementation(libs.kotlin.dev.scriptingJvm)
    runtimeOnly(libs.logging.logback.classic)
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
}
