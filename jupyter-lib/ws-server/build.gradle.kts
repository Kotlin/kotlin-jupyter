plugins {
    kotlin("libs.publisher")
    kotlin("jupyter.api")
    kotlin("jvm")
}

dependencies {
    api(projects.api) { isTransitive = false }
    api(projects.lib) { isTransitive = false }
    api(projects.commonDependencies) { isTransitive = false }
    api(projects.sharedCompiler) { isTransitive = false }
    implementation(libs.serialization.json)
    implementation(libs.java.websocket)

    // Test dependencies: kotlin-test and Junit 5
    testImplementation(libs.test.junit.params)
    testImplementation(libs.test.kotlintest.assertions)
}

kotlinPublications {
    publication {
        publicationName.set("ws-server")
        description.set("Kotlin Jupyter kernel with WebSocket protocol implementation")
    }
}
