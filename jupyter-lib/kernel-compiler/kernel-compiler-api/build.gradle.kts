plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("libs.publisher")
}

dependencies {
    // API module for shared types
    api(projects.api)
    api(projects.commonDependencies)
    api(projects.protocolApi) // For KernelLoggerFactory

    // Kotlin scripting for ScriptDiagnostic
    api(libs.kotlin.dev.scriptingCommon)
    api(libs.kotlin.dev.scriptingJvm)

    // Serialization for API data classes
    api(libs.serialization.cbor)

    // Coroutines for async API
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
        publicationName.set("kernel-compiler-api")
        description.set("Implementation-agnostic API for communication between the kernel and REPL compiler")
    }
}
