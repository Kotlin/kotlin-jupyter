import org.jetbrains.kotlin.jupyter.publishing.addPublication

plugins {
    id("org.jetbrains.kotlin.jupyter.publishing")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

project.version = rootProject.version
val kotlinxSerializationVersion: String by rootProject
val slf4jVersion: String by rootProject
val junitVersion: String by rootProject
val khttpVersion: String by rootProject

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    // Internal dependencies
    api(project(":kotlin-jupyter-api"))
    api(project(":kotlin-jupyter-lib"))
    api(project(":kotlin-jupyter-deps"))

    // Standard dependencies
    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("stdlib-jdk8"))
    compileOnly(kotlin("reflect"))

    // Scripting and compilation-related dependencies
    compileOnly(kotlin("scripting-common"))
    compileOnly(kotlin("scripting-jvm"))
    compileOnly(kotlin("scripting-compiler-impl"))
    implementation(kotlin("scripting-dependencies") as String) { isTransitive = false }

    // Serialization compiler plugin (for notebooks, not for kernel code)
    compileOnly(kotlin("serialization-unshaded"))

    // Logging
    compileOnly("org.slf4j:slf4j-api:$slf4jVersion")

    // Khttp for resolving remote library dependencies
    implementation("khttp:khttp:$khttpVersion")

    // Serialization implementation for kernel code
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

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
    inputs.property("version", version)

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

addPublication {
    publicationName = "compiler"
    artifactId = "compiler"
    bintrayDescription = "Compiler helpers for Kotlin Jupyter notebooks"
    bintrayPackageName = "kotlin-jupyter-compiler"
}
