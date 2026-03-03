import com.google.protobuf.gradle.id

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.protobuf)
    kotlin("libs.publisher")
}

dependencies {
    // API module for shared types
    api(projects.api)
    api(projects.commonDependencies)
    // api(projects.intellijCompilerDependencies)

    // Kotlin scripting for ScriptDiagnostic
    api(libs.kotlin.dev.scriptingCommon)
    api(libs.kotlin.dev.scriptingJvm)

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
        artifact =
            libs
                .protobuf
                .protoc
                .get()
                .toString()
    }
    plugins {
        id("grpc") {
            artifact =
                libs
                    .grpc
                    .protoc
                    .gen
                    .java
                    .get()
                    .toString()
        }
        id("grpckt") {
            artifact =
                "${
                    libs
                        .grpc
                        .protoc
                        .gen
                        .kotlin
                        .get()
                }:jdk8@jar"
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

kotlinPublications {
    publication {
        publicationName.set("kernel-compiler-api")
        description.set("Set of API for communication between the kernel and REPL compiler")
    }
}
