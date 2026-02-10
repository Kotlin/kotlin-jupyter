import com.google.protobuf.gradle.id

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    // API module for shared types
    api(projects.api)
    api(projects.commonDependencies)

    // Serialization for API data classes
    api(libs.serialization.json)

    // gRPC dependencies
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.grpc.kotlin.stub)
    api(libs.protobuf.kotlin)

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

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.28.3"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.68.1"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
                id("grpckt")
            }
            it.builtins {
                id("kotlin")
            }
        }
    }
}
