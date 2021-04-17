import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlinx.jupyter.build.getFlag
import org.jetbrains.kotlinx.jupyter.plugin.options
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import ru.ileasile.kotlin.apache2
import ru.ileasile.kotlin.developer
import ru.ileasile.kotlin.githubRepo

plugins {
    kotlin("jvm")
    kotlin("jupyter.api") apply false
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.kotlinx.jupyter.dependencies")
    id("ru.ileasile.kotlin.publisher")
    id("ru.ileasile.kotlin.doc")
}

extra["isMainProject"] = true

val kotlinVersion: String by project
val kotlinxSerializationVersion: String by project
val ktlintVersion: String by project
val junitVersion: String by project
val slf4jVersion: String by project
val logbackVersion: String by project

val docsRepo: String by project

val taskOptions = project.options()
val deploy: Configuration by configurations.creating

deploy.apply {
    exclude("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm")
    exclude("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm")
}

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
    val stableKotlinLanguageLevel: String by rootProject
    val jvmTarget: String by rootProject

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            languageVersion = stableKotlinLanguageLevel
            this.jvmTarget = jvmTarget
        }
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = jvmTarget
        targetCompatibility = jvmTarget
    }
}

tasks.withType<KotlinCompile> {
    val kotlinLanguageLevel: String by rootProject
    kotlinOptions {
        languageVersion = kotlinLanguageLevel
    }
}

dependencies {
    // Dependency on module with compiler.
    api(project(":shared-compiler"))

    fun implKotlin(module: String, version: String? = kotlinVersion) = implementation(kotlin(module, version))

    // Standard dependencies
    implKotlin("stdlib", null)
    implKotlin("reflect", null)
    implKotlin("stdlib-jdk8", null)
    implementation("org.jetbrains:annotations:20.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")

    // Embedded compiler and scripting dependencies
    implKotlin("compiler-embeddable")
    implKotlin("scripting-compiler-impl-embeddable")
    implKotlin("scripting-compiler-embeddable")
    implKotlin("scripting-ide-services")
    implKotlin("main-kts")
    implKotlin("script-util")
    implKotlin("scripting-common")

    // Embedded version of serialization plugin for notebook code
    implKotlin("serialization")

    // Logging
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

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
    deploy(kotlin("script-runtime", kotlinVersion))
}

tasks.register("publishToPluginPortal") {
    group = "publishing"

    dependsOn(":kotlin-jupyter-api-gradle-plugin:publishPlugins")
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
    if (!getFlag("skipReadmeCheck", false)) {
        dependsOn(tasks.checkReadme)
    }
}

tasks.publishDocs {
    docsRepoUrl.set(docsRepo)
    branchName.set("master")
    username.set("robot")
    email.set("robot@jetbrains.com")
}

kotlinPublications {
    packageGroup = "org.jetbrains.kotlinx"

    fun prop(name: String) = project.findProperty(name) as? String?

    sonatypeSettings(
        prop("kds.sonatype.user"),
        prop("kds.sonatype.password"),
        "kotlin-jupyter project, v. ${project.version}"
    )

    signingCredentials(
        prop("kds.sign.key.id"),
        prop("kds.sign.key.private"),
        prop("kds.sign.key.passphrase")
    )

    pom {
        githubRepo("Kotlin", "kotlin-jupyter")

        inceptionYear.set("2021")

        licenses {
            apache2()
        }

        developers {
            developer("nikitinas", "Anatoly Nikitin", "Anatoly.Nikitin@jetbrains.com")
            developer("ileasile", "Ilya Muradyan", "Ilya.Muradyan@jetbrains.com")
        }
    }

    publication {
        publicationName = "kernel"
        artifactId = "kotlin-jupyter-kernel"
        description = "Kotlin Jupyter kernel published as artifact"
        packageName = artifactId
    }
}

tasks.named("publishLocal") {
    dependsOn(
        tasks.condaPackage,
        tasks.pyPiPackage
    )
}
