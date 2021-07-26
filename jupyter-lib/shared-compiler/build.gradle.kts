import build.CreateResourcesTask

plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    // Internal dependencies
    api(projects.api)
    api(projects.lib)
    api(projects.commonDependencies)

    // Standard dependencies
    compileOnly(libs.kotlin.stable.stdlib)
    compileOnly(libs.kotlin.stable.stdlibJdk8)
    compileOnly(libs.kotlin.stable.reflect)

    // Scripting and compilation-related dependencies
    compileOnly(libs.kotlin.dev.scriptingCommon)
    compileOnly(libs.kotlin.dev.scriptingJvm)
    compileOnly(libs.kotlin.dev.scriptingCompilerImplUnshaded)
    implementation(libs.kotlin.dev.scriptingDependencies) { isTransitive = false }

    // Serialization compiler plugin (for notebooks, not for kernel code)
    compileOnly(libs.serialization.dev.unshadedPlugin)

    // Logging
    compileOnly(libs.logging.slf4j.api)
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withCompilerArgs {
        skipPrereleaseCheck()
    }
    withTests()
}

val buildProperties by tasks.creating(CreateResourcesTask::class) {
    setupDependencies(tasks.processResources)
    addPropertiesFile(
        "compiler.properties",
        mapOf(
            "version" to rootSettings.pyPackageVersion
        )
    )
}

kotlinPublications {
    publication {
        publicationName.set("compiler")
        description.set("Implementation of REPL compiler and preprocessor for Jupyter dialect of Kotlin (IDE-compatible)")
    }
}
