import com.github.jengelman.gradle.plugins.shadow.transformers.ComponentsXmlResourceTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlinx.jupyter.build.getFlag
import org.jetbrains.kotlinx.jupyter.plugin.options
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import ru.ileasile.kotlin.apache2
import ru.ileasile.kotlin.developer
import ru.ileasile.kotlin.githubRepo

plugins {
    id("org.jetbrains.kotlinx.jupyter.dependencies")
}

extra["isMainProject"] = true

val ktlintVersion: String by project
val docsRepo: String by project
val githubRepoUser: String by project
val githubRepoName: String by project

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
    val stableKotlinLanguageLevel: String by rootProject
    val jvmTarget: String by rootProject

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            apiVersion = stableKotlinLanguageLevel
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
        apiVersion = kotlinLanguageLevel

        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += listOf("-Xskip-prerelease-check")
    }
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
    testImplementation(libs.kotlin.stable.test)
    testImplementation(libs.test.junit.api)
    testImplementation(libs.test.junit.params)
    testImplementation(libs.test.kotlintest.assertions)

    testRuntimeOnly(libs.test.junit.engine)

    deploy(projects.lib)
    deploy(projects.api)
    deploy(libs.kotlin.dev.scriptRuntime.get())
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
    transform(ComponentsXmlResourceTransformer())

    manifest {
        attributes(tasks.jar.get().manifest.attributes)
    }
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

val createTestResources: Task by tasks.creating {
    val jupyterApiVersion: String by project

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

changelog {
    githubUser = githubRepoUser
    githubRepository = githubRepoName
    excludeLabels = listOf("wontfix", "duplicate", "no-changelog", "question")
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
        tasks.pyPiPackage,
    )
}
