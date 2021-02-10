import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlinx.jupyter.build.getFlag
import org.jetbrains.kotlinx.jupyter.plugin.options
import org.jetbrains.kotlinx.jupyter.publishing.applyNexusPlugin
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.kotlinx.jupyter.dependencies")
    id("org.jetbrains.kotlinx.jupyter.publishing") apply false
    id("org.jetbrains.kotlinx.jupyter.doc")
}

extra["isMainProject"] = true

val kotlinxSerializationVersion: String by project
val ktlintVersion: String by project
val junitVersion: String by project
val slf4jVersion: String by project
val khttpVersion: String by project

val docsRepo: String by project

val taskOptions = project.options()
val deploy: Configuration by configurations.creating

applyNexusPlugin()

fun KtlintExtension.setup() {
    version.set(ktlintVersion)
    enableExperimentalRules.set(true)
}

ktlint {
    setup()
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    ktlint {
        setup()
    }
}

allprojects {
    val kotlinLanguageLevel: String by rootProject
    val jvmTarget: String by rootProject

    tasks.withType(KotlinCompile::class.java).all {
        kotlinOptions {
            languageVersion = kotlinLanguageLevel
            this.jvmTarget = jvmTarget
        }
    }
}

dependencies {
    // Dependency on module with compiler.
    implementation(project(":shared-compiler"))

    // Standard dependencies
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains:annotations:20.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")

    // Embedded compiler and scripting dependencies
    implementation(kotlin("compiler-embeddable"))
    implementation(kotlin("scripting-compiler-impl-embeddable"))
    implementation(kotlin("scripting-compiler-embeddable"))
    implementation(kotlin("scripting-ide-services"))
    implementation(kotlin("main-kts"))
    implementation(kotlin("script-util"))
    implementation(kotlin("scripting-common"))

    // Embedded version of serialization plugin for notebook code
    implementation(kotlin("serialization"))

    // Logging
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    runtimeOnly("org.slf4j:slf4j-simple:$slf4jVersion")

    // ZeroMQ library for implementing messaging protocol
    implementation("org.zeromq:jeromq:0.5.2")

    // Clikt library for parsing output magics
    implementation("com.github.ajalt:clikt:2.8.0")

    // Serialization implementation for kernel code
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    // Test dependencies: kotlin-test and Junit 5
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("io.kotlintest:kotlintest-assertions:3.1.6")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    deploy(project(":lib"))
    deploy(project(":api"))
    deploy(kotlin("script-runtime"))
}

tasks.register("publishLocal") {
    group = "publishing"

    dependsOn(
        tasks.condaPackage,
        tasks.pyPiPackage
    )
}

val publishToSonatype by tasks.registering {
    group = "publishing"
}

tasks.named("closeRepository") {
    mustRunAfter(publishToSonatype)
}

tasks.register("publishToSonatypeAndRelease") {
    group = "publishing"

    dependsOn(publishToSonatype, "closeAndReleaseRepository")
}

tasks.register("publishToPluginPortal") {
    group = "publishing"

    dependsOn(
        ":api-gradle-plugin:publishPlugins"
    )
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = taskOptions.mainClassFQN
        attributes["Implementation-Version"] = project.version
    }
}

tasks.shadowJar {
    archiveBaseName.set(taskOptions.packageName)
    archiveClassifier.set("")
    mergeServiceFiles()

    manifest {
        attributes(tasks.jar.get().manifest.attributes)
    }
}

tasks.test {
    val doParallelTesting = getFlag("test.parallel", true)

    /**
     *  Set to true to debug classpath/shadowing issues, see testKlaxonClasspathDoesntLeak test
     */
    val useShadowedJar = getFlag("test.useShadowed", false)

    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }

    if (useShadowedJar) {
        dependsOn(tasks.shadowJar.get())
        classpath = files(tasks.shadowJar.get()) + classpath
    }

    systemProperties = mutableMapOf(
        "junit.jupiter.displayname.generator.default" to "org.junit.jupiter.api.DisplayNameGenerator\$ReplaceUnderscores",

        "junit.jupiter.execution.parallel.enabled" to doParallelTesting.toString() as Any,
        "junit.jupiter.execution.parallel.mode.default" to "concurrent",
        "junit.jupiter.execution.parallel.mode.classes.default" to "concurrent"
    )
}

tasks.processResources {
    dependsOn(tasks.buildProperties)
}

tasks.check {
    dependsOn(tasks.checkReadme)
}

tasks.publishDocs {
    docsRepoUrl.set(docsRepo)
}
