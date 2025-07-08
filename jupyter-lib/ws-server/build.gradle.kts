plugins {
    kotlin("libs.publisher")
    kotlin("jupyter.api")
    kotlin("jvm")
}

dependencies {
    // excluding it from transitive propagation
    compileOnly(libs.kotlin.stable.stdlib)

    api(projects.sharedCompiler)
    compileOnly(libs.serialization.json)
    compileOnly(libs.java.websocket)
}

kotlinPublications {
    publication {
        publicationName.set("ws-server")
        description.set("Kotlin Jupyter kernel with WebSocket protocol implementation")
    }
}
