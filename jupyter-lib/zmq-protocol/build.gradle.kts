import build.util.compileOnly

plugins {
    kotlin("libs.publisher")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

// When adding new dependencies, make sure that
// kotlin-stdlib and kotlin-reflect dependencies do not get into the POM file
dependencies {
    // Internal dependencies
    api(projects.protocol) { isTransitive = false }
    api(projects.protocolApi) { isTransitive = false }

    // Standard dependencies
    compileOnly(libs.kotlin.stable.stdlib)
    compileOnly(libs.kotlin.stable.stdlibJdk8)

    // Serialization runtime
    compileOnly(libs.serialization.json)

    // Logging
    compileOnly(libs.logging.slf4j.api)

    // ZeroMQ library for Jupyter messaging protocol implementation
    api(libs.zeromq)
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withCompilerArgs {
        skipPrereleaseCheck()
        jdkRelease(rootSettings.jvmTarget)
    }
    withTests()
}

kotlinPublications {
    publication {
        publicationName.set("zmq-protocol")
        description.set("ZeroMQ-specific implementation of Jupyter protocol for kernel communication")
    }
}
