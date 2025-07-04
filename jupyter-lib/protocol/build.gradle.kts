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
    api(projects.lib) { isTransitive = false }
    api(projects.commonDependencies) { isTransitive = false }

    // Standard dependencies
    compileOnly(libs.kotlin.stable.stdlib)
    compileOnly(libs.kotlin.stable.stdlibJdk8)
    compileOnly(libs.kotlin.stable.reflect)

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
        publicationName.set("jupyter-protocol")
        description.set("JP")
    }
}
