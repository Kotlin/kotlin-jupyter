import build.util.compileOnly

plugins {
    kotlin("libs.publisher")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

// When adding new dependencies, make sure that
// kotlin-stdlib and kotlin-reflect dependencies do not get into the POM file
dependencies {
    // Standard dependencies
    compileOnly(libs.kotlin.stable.stdlib)
    compileOnly(libs.kotlin.stable.stdlibJdk8)

    // Serialization runtime
    compileOnly(libs.serialization.json)

    // Logging
    compileOnly(libs.logging.slf4j.api)
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
        publicationName.set("protocol-api")
        description.set("Core protocol abstractions and basic types for Jupyter kernel communication")
    }
}
