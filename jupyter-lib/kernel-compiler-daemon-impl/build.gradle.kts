plugins {
    kotlin("jvm")
    application
}

dependencies {
    api(projects.kernelCompilerApi)
    api(projects.kernelCompilerImpl)
    api(projects.api)
    api(projects.commonDependencies)

    // gRPC dependencies
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

application {
    mainClass.set("org.jetbrains.kotlinx.jupyter.compiler.daemon.CompilerDaemonMainKt")
}

// Task to create a fat jar for the daemon
tasks.register<Jar>("daemonJar") {
    archiveClassifier.set("daemon")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "org.jetbrains.kotlinx.jupyter.compiler.daemon.CompilerDaemonMainKt"
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    from(sourceSets.main.get().output)
}
