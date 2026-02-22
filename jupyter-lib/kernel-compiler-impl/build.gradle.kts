plugins {
    kotlin("jvm")
    alias(libs.plugins.shadow)
}

dependencies {
    api(projects.kernelCompilerApi)
    api(projects.api)
    api(projects.intellijCompilerDependencies) // For getCompilationConfiguration
    api(projects.protocolApi) // For KernelLoggerFactory

    // Coroutines for async operations
    implementation(libs.coroutines.core)

    // Logging
    implementation(libs.logging.slf4j.api)

    // Kotlin scripting dependencies for actual compilation
    implementation(libs.kotlin.dev.compilerEmbeddable)
    implementation(libs.kotlin.dev.scriptingCompilerImplEmbeddable)
    implementation(libs.kotlin.dev.scriptingCompilerEmbeddable)
    implementation(libs.kotlin.dev.scriptingCommon)
    implementation(libs.kotlin.dev.scriptingJvm)
    implementation(libs.kotlin.dev.scriptRuntime)
    implementation(libs.kotlin.dev.reflect)
    implementation(libs.kotlin.dev.scriptingIdeServices)

    // trove4j is a dependency of compiler-embeddable
    implementation(libs.jetbrains.trove4j)

    // gRPC dependencies for daemon server implementation
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.grpc.netty)
    implementation(libs.protobuf.kotlin)

    // Test dependencies
    testImplementation(libs.kotlin.stable.test)
    testImplementation(libs.test.junit.api)
    testRuntimeOnly(libs.test.junit.engine)
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withCompilerArgs {
        skipPrereleaseCheck()
        jdkRelease(rootSettings.jvmTarget)
    }
}

tasks.test {
    useJUnitPlatform()
}

// Task to create a fat jar for the compiler daemon
tasks.shadowJar {
    archiveClassifier.set("daemon")
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    manifest {
        attributes["Main-Class"] = "org.jetbrains.kotlinx.jupyter.compiler.impl.CompilerDaemonMainKt"
    }
}
