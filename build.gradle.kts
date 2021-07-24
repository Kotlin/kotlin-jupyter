import build.getFlag
import build.typedProperty
import build.withCompilerArgs
import build.withJvmTarget
import build.withLanguageLevel
import build.withTests
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import ru.ileasile.kotlin.apache2
import ru.ileasile.kotlin.developer
import ru.ileasile.kotlin.githubRepo

plugins {
    id("build.plugins.main")
}

val deploy: Configuration by configurations.creating

deploy.apply {
    exclude("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm")
    exclude("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm")
}

fun KtlintExtension.setup() {
    version.set(libs.versions.ktlint)
    enableExperimentalRules.set(true)
}

ktlint {
    setup()
    filter {
        exclude("**/org/jetbrains/kotlinx/jupyter/repl.kt")
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    ktlint {
        setup()
    }
}

allprojects {
    withJvmTarget(rootProject.options.jvmTarget)
    withLanguageLevel(rootProject.options.stableKotlinLanguageLevel)
}

withLanguageLevel(options.kotlinLanguageLevel)
withCompilerArgs {
    skipPrereleaseCheck()
}

dependencies {
    // Dependency on module with compiler.
    api(projects.sharedCompiler)

    // Standard dependencies
    implementation(libs.kotlin.dev.reflect)
    implementation(libs.kotlin.dev.stdlibJdk8)
    implementation(libs.jetbrains.annotations)
    implementation(libs.coroutines.core)

    // Embedded compiler and scripting dependencies
    implementation(libs.kotlin.dev.compilerEmbeddable)
    implementation(libs.kotlin.dev.scriptingCompilerImplEmbeddable)
    implementation(libs.kotlin.dev.scriptingCompilerEmbeddable)
    implementation(libs.kotlin.dev.scriptingIdeServices)
    implementation(libs.kotlin.dev.scriptingDependenciesMaven)
    implementation(libs.kotlin.dev.scriptUtil)
    implementation(libs.kotlin.dev.scriptingCommon)

    // Embedded version of serialization plugin for notebook code
    implementation(libs.serialization.dev.embeddedPlugin)

    // Logging
    implementation(libs.logging.slf4j.api)
    implementation(libs.logging.logback.classic)

    // ZeroMQ library for implementing messaging protocol
    implementation(libs.zeromq)

    // Clikt library for parsing output magics
    implementation(libs.clikt)

    // Serialization implementation for kernel code
    implementation(libs.serialization.json)

    // Test dependencies: kotlin-test and Junit 5
    testImplementation(libs.test.junit.params)
    testImplementation(libs.test.kotlintest.assertions)

    deploy(projects.lib)
    deploy(projects.api)
    deploy(libs.kotlin.dev.scriptRuntime.get())
}

withTests()

tasks.register("publishToPluginPortal") {
    group = build.PUBLISHING_GROUP

    dependsOn(":kotlin-jupyter-api-gradle-plugin:publishPlugins")
}

// Workaround for https://github.com/johnrengelman/shadow/issues/651
components.withType(AdhocComponentWithVariants::class.java).forEach { c ->
    c.withVariantsFromConfiguration(project.configurations.shadowRuntimeElements.get()) {
        skip()
    }
}

tasks.test {
    val doParallelTesting = getFlag("test.parallel", true)
    maxHeapSize = "2048m"

    /**
     *  Set to true to debug classpath/shadowing issues, see testKlaxonClasspathDoesntLeak test
     */
    val useShadowedJar = getFlag("test.useShadowed", false)

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

val createTestResources: Task by tasks.creating {
    val jupyterApiVersion = libs.versions.jupyterApi.get()

    inputs.property("jupyterApiVersion", jupyterApiVersion)

    val outputDir = file(project.buildDir.toPath().resolve("resources").resolve("test"))
    outputs.dir(outputDir)

    doLast {
        outputDir.mkdirs()
        val propertiesFile = outputDir.resolve("PUBLISHED_JUPYTER_API_VERSION")
        propertiesFile.writeText(jupyterApiVersion)
    }
}

tasks.processTestResources {
    dependsOn(createTestResources)
}

tasks.publishDocs {
    docsRepoUrl.set(options.docsRepo)
    branchName.set("master")
    username.set("robot")
    email.set("robot@jetbrains.com")
}

changelog {
    githubUser = options.githubRepoUser
    githubRepository = options.githubRepoName
    excludeLabels = listOf("wontfix", "duplicate", "no-changelog", "question")
}

kotlinPublications {
    packageGroup.set("org.jetbrains.kotlinx")
    defaultArtifactIdPrefix.set("kotlin-jupyter-")

    sonatypeSettings(
        typedProperty("kds.sonatype.user"),
        typedProperty("kds.sonatype.password"),
        "kotlin-jupyter project, v. ${project.version}"
    )

    signingCredentials(
        typedProperty("kds.sign.key.id"),
        typedProperty("kds.sign.key.private"),
        typedProperty("kds.sign.key.passphrase")
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

    repositories {
        defaultLocalRepository()
    }

    publication {
        publicationName.set("kernel")
        description.set("Kotlin Jupyter kernel published as artifact")
    }
}
