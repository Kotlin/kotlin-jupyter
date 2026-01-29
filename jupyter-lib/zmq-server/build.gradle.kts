import build.util.compileOnly

plugins {
    kotlin("libs.publisher")
    kotlin("jvm")
}

dependencies {
    api(projects.zmqProtocol)
    api(projects.protocol)
    api(projects.sharedCompiler)

    compileOnly(libs.kotlin.stable.stdlib)
    compileOnly(libs.serialization.json)
    compileOnly(libs.logging.slf4j.api)
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
        publicationName.set("zmq-server")
        description.set("ZeroMQ-based Kotlin Jupyter server runner for kernel communication")
    }
}
