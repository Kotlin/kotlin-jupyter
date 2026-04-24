import build.util.compileOnly
import build.util.excludeKotlinDependencies

plugins {
    kotlin("libs.publisher")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

// When adding new dependencies, make sure that
// kotlin-stdlib and kotlin-reflect dependencies do not get into POM file
dependencies {
    // Internal dependencies
    api(projects.api) { isTransitive = false }
    api(projects.protocolApi) { isTransitive = false }
    api(projects.lib) { isTransitive = false }
    api(projects.commonDependencies) { isTransitive = false }
    api(projects.protocol) { isTransitive = false }
    api(projects.intellijCompilerDependencies) { isTransitive = false }
    api(projects.kernelCompilerApi) { isTransitive = false }

    // Standard dependencies
    compileOnly(libs.kotlin.stable.stdlib)
    compileOnly(libs.kotlin.stable.stdlibJdk8)
    compileOnly(libs.kotlin.stable.reflect)

    // Scripting and compilation-related dependencies
    compileOnly(libs.kotlin.dev.scriptingCommon)
    compileOnly(libs.kotlin.dev.scriptingJvm)
    compileOnly(libs.kotlin.dev.scriptingCompilerImplUnshaded)
    // K2: Is here because we need to reference org.jetbrains.kotlin.scripting.compiler.plugin.repl.configuration.configureDefaultRepl
    compileOnly(libs.kotlin.dev.scriptingCompiler)

    // Serialization runtime
    compileOnly(libs.serialization.json)

    // Coroutines
    compileOnly(libs.coroutines.core)

    // Serialization compiler plugin (for notebooks, not for kernel code)
    compileOnly(libs.serialization.dev.unshaded)

    // Logging
    compileOnly(libs.logging.slf4j.api)

    // Clikt library for parsing output magics
    implementation(libs.clikt) {
        excludeKotlinDependencies(
            "stdlib",
            "stdlib-common",
        )
    }
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
        publicationName.set("intellij-dependencies-shared")
        description.set("Implementation of REPL compiler and preprocessor for Jupyter dialect of Kotlin (IDE-compatible)")
    }
}
