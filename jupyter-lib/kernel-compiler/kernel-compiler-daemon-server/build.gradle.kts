plugins {
    kotlin("jvm")
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlinx.rpc)
}

dependencies {
    api(projects.kernelCompilerApi)
    api(projects.kernelCompilerDaemonApi)
    api(projects.kernelCompilerImpl)
    api(projects.api)
    api(projects.protocolApi) // For KernelLoggerFactory

    // Coroutines for async operations
    implementation(libs.coroutines.core)

    // Logging
    implementation(libs.logging.slf4j.api)
    runtimeOnly(libs.logging.logback.classic)

    // kotlinx.rpc dependencies
    implementation(libs.kotlinx.rpc.krpc.client)
    implementation(libs.kotlinx.rpc.krpc.server)
    implementation(libs.kotlinx.rpc.krpc.serialization.cbor)
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withCompilerArgs {
        add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
        skipPrereleaseCheck()
        jdkRelease(rootSettings.jvmTarget)
    }
}

val copyLogbackConfig =
    tasks.register<Copy>("copyLogbackConfig") {
        from(rootDir.resolve("logback.xml"))
        into(layout.buildDirectory.dir("resources/main"))
    }

tasks.processResources {
    dependsOn(copyLogbackConfig)
}

// Task to create a fat jar for the compiler daemon
tasks.shadowJar {
    archiveClassifier.set("daemon")
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    manifest {
        attributes["Main-Class"] = "org.jetbrains.kotlinx.jupyter.compiler.daemon.server.CompilerDaemonMainKt"
    }
}
