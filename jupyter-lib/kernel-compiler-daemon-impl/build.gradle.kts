plugins {
    kotlin("jvm")
}

// This module contains the daemon LAUNCHER (client side) - NOT the daemon implementation.
// The actual compiler implementation is in kernel-compiler-impl.
// kernel-compiler-daemon-impl should NOT depend on kernel-compiler-impl.

dependencies {
    api(projects.kernelCompilerApi)
    api(projects.api)
    api(projects.commonDependencies)

    // gRPC dependencies for client
    implementation(libs.grpc.netty)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.protobuf.kotlin)

    // Coroutines
    implementation(libs.coroutines.core)

    // Logging
    implementation(libs.logging.slf4j.api)
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withCompilerArgs {
        skipPrereleaseCheck()
        jdkRelease(rootSettings.jvmTarget)
    }
}

// Copy the daemon fat jar from kernel-compiler-impl into this module's resources
val copyDaemonJar by tasks.registering(Copy::class) {
    dependsOn(":kernel-compiler-impl:shadowJar")
    from(project(":kernel-compiler-impl").tasks.named("shadowJar"))
    into(layout.buildDirectory.dir("resources/main"))
    rename { "compiler-daemon.jar" }
}

tasks.named("processResources") {
    dependsOn(copyDaemonJar)
}
