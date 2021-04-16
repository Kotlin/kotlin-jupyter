import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

project.version = rootProject.version

val kotlinVersion: String by rootProject
val slf4jVersion: String by rootProject
val junitVersion: String by rootProject

tasks.withType(KotlinCompile::class.java) {
    val kotlinLanguageLevel: String by rootProject
    kotlinOptions {
        languageVersion = kotlinLanguageLevel
    }
}

repositories {
    mavenCentral()
}

dependencies {
    fun compileOnlyKotlin(module: String, version: String? = kotlinVersion) = compileOnly(kotlin(module, version))

    // Internal dependencies
    api(project(":api"))
    api(project(":lib"))
    api(project(":common-dependencies"))

    // Standard dependencies
    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("stdlib-jdk8"))
    compileOnly(kotlin("reflect"))

    // Scripting and compilation-related dependencies
    compileOnlyKotlin("scripting-common")
    compileOnlyKotlin("scripting-jvm")
    compileOnlyKotlin("scripting-compiler-impl")
    implementation(kotlin("scripting-dependencies", kotlinVersion) as String) { isTransitive = false }

    // Serialization compiler plugin (for notebooks, not for kernel code)
    compileOnlyKotlin("serialization-unshaded")

    // Logging
    compileOnly("org.slf4j:slf4j-api:$slf4jVersion")

    // Testing dependencies: kotlin-test and JUnit 5
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
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
