plugins {
    kotlin("jvm")
    kotlin("libs.publisher")
    alias(libs.plugins.kotlinx.rpc)
}

// This module contains the client that communicates with the compiler daemon.
// The actual compiler daemon implementation is in kernel-compiler-daemon-server.

dependencies {
    api(projects.kernelCompilerDaemonApi)
    api(projects.api)
    api(projects.commonDependencies)
    api(projects.protocol)

    // kotlinx.rpc dependencies
    implementation(libs.kotlinx.rpc.krpc.client)
    implementation(libs.kotlinx.rpc.krpc.server)
    implementation(libs.kotlinx.rpc.krpc.serialization.cbor)

    // Coroutines
    implementation(libs.coroutines.core)

    // Logging
    implementation(libs.logging.slf4j.api)
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withCompilerArgs {
        add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
        skipPrereleaseCheck()
        jdkRelease(rootSettings.jvmTarget)
    }
}

// Copy the daemon fat jar from kernel-compiler-daemon-server into this module's resources
val copyDaemonJar by tasks.registering(Copy::class) {
    dependsOn(":kernel-compiler-daemon-server:shadowJar")
    from(project(":kernel-compiler-daemon-server").tasks.named("shadowJar"))
    into(layout.buildDirectory.dir("resources/main"))
    rename { "compiler-daemon.jar" }
}

tasks.named("processResources") {
    dependsOn(copyDaemonJar)
}

kotlinPublications {
    publication {
        publicationName.set("kernel-compiler-daemon-client")
        description.set("Client for communication with the REPL compiler daemon")
    }
}
