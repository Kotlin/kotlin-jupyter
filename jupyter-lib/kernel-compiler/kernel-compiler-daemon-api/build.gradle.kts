plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.kotlinx.rpc)
    kotlin("libs.publisher")
}

dependencies {
    api(projects.kernelCompilerApi)

    api(libs.kotlinx.rpc.krpc.core)

    // WebSocket for custom transport
    api(libs.java.websocket)

    api(libs.coroutines.core)
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withCompilerArgs {
        skipPrereleaseCheck()
        jdkRelease(rootSettings.jvmTarget)
    }
}

kotlinPublications {
    publication {
        publicationName.set("kernel-compiler-daemon-api")
        description.set("API for communication between the kernel and REPL compiler daemon")
    }
}
