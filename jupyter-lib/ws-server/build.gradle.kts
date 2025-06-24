@file:Suppress("UnstableApiUsage")


plugins {
    kotlin("libs.publisher")
    kotlin("jupyter.api")
    kotlin("jvm")
}


dependencies {
    implementation(projects.kotlinJupyterKernel)
    // implementation(libs.kotlin.dev.reflect)
    implementation(libs.serialization.json)
    implementation(libs.java.websocket)

    // Test dependencies: kotlin-test and Junit 5
    testImplementation(libs.test.junit.params)
    testImplementation(libs.test.kotlintest.assertions)
}

buildSettings {
    withJvmTarget("11")
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of("11"))
    }
}

kotlinPublications {
    publication {
        publicationName.set("ws-server")
        description.set("Kotlin Jupyter kernel with WebSocket protocol implementation")
    }
}
