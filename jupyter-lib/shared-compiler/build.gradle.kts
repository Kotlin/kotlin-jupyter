import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

project.version = rootProject.version

tasks.withType<KotlinCompile> {
    val kotlinLanguageLevel: String by rootProject
    kotlinOptions {
        languageVersion = kotlinLanguageLevel
        apiVersion = kotlinLanguageLevel

        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += listOf("-Xskip-prerelease-check")
    }
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

    // Testing dependencies: kotlin-test and JUnit 5
    testImplementation(libs.kotlin.stable.test)
    testImplementation(libs.test.junit.api)
    testRuntimeOnly(libs.test.junit.engine)
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

val buildProperties by tasks.registering {
    inputs.property("version", rootProject.findProperty("pythonVersion"))

    val outputDir = file(project.buildDir.toPath().resolve("resources").resolve("main"))
    outputs.dir(outputDir)

    doLast {
        outputDir.mkdirs()
        val properties = inputs.properties.entries.map { it.toPair() }.toMutableList()
        val propertiesFile = outputDir.resolve("compiler.properties")
        propertiesFile.writeText(properties.joinToString("") { "${it.first}=${it.second}\n" })
    }
}

tasks.processResources {
    dependsOn(buildProperties)
}

kotlinPublications {
    publication {
        publicationName = "compiler"
        artifactId = "kotlin-jupyter-shared-compiler"
        description = "Implementation of REPL compiler and preprocessor for Jupyter dialect of Kotlin (IDE-compatible)"
        packageName = artifactId
    }
}
