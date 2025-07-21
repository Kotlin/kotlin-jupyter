@file:Suppress("UnstableApiUsage")

import build.CompilerRelocatedJarConfigurator
import build.CreateResourcesTask
import build.PUBLISHING_GROUP
import build.util.excludeStandardKotlinDependencies
import build.util.getFlag
import build.util.typedProperty
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.shadowJar
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.shadowRuntimeElements
import com.github.jengelman.gradle.plugins.shadow.transformers.ComponentsXmlResourceTransformer
import org.gradle.kotlin.dsl.accessors.runtime.addConfiguredDependencyTo
import org.jetbrains.gradle.shadow.registerShadowJarTasksBy
import org.jetbrains.kotlinx.publisher.apache2
import org.jetbrains.kotlinx.publisher.composeOfTaskOutputs
import org.jetbrains.kotlinx.publisher.developer
import org.jetbrains.kotlinx.publisher.githubRepo
import java.util.Properties

plugins {
    id("build.plugins.main")
}

val deploy: Configuration by configurations.creating

val kernelShadowed: Configuration by configurations.creating
val embeddableKernel: Configuration by configurations.creating
val scriptClasspathShadowed: Configuration by configurations.creating
val ideScriptClasspathShadowed: Configuration by configurations.creating

val spaceUsername: String by properties
val spaceToken: String by properties

// Changes here should also be reflected in `KernelBuildConfigurator.setupKtLintForAllProjects()`
ktlint {
    version.set(libs.versions.ktlint.get())
    filter {
        exclude("**/org/jetbrains/kotlinx/jupyter/repl.kt")
    }
}

val sharedProps =
    Properties().apply {
        load(File(rootDir, "shared.properties").inputStream())
    }

repositories {
    mavenCentral()
    maven(sharedProps.getProperty("kotlin.repository"))
    maven(sharedProps.getProperty("kotlin.ds.repository"))
    maven {
        name = "intellij-deps"
        url = uri("https://www.jetbrains.com/intellij-repository/releases/")
    }
    if (System.getenv("KOTLIN_JUPYTER_USE_MAVEN_LOCAL") != null) {
        mavenLocal()
    }
}

@Suppress("ktlint:standard:chain-method-continuation")
dependencies {
    // Required by K2KJvmReplCompilerWithCompletion.
    // Should be moved to Kotlin Compiler eventually once complete
    compileOnly(libs.intellij.platform.util)

    implementation(libs.kotlin.dev.stdlib)

    // Dependency on module with compiler.
    api(projects.sharedCompiler)
    api(projects.zmqProtocol)

    // Standard dependencies
    implementation(libs.kotlin.dev.reflect)
    implementation(libs.jetbrains.annotations)
    implementation(libs.coroutines.core)

    // Embedded compiler and scripting dependencies
    addSharedEmbeddedDependenciesTo(configurations.implementation.get())
    implementation(libs.kotlin.dev.scriptingCommon)
    implementation(libs.kotlin.dev.scriptingJvm)
    // Dependency of `libs.kotlin.dev.compilerEmbeddable`
    implementation(libs.jetbrains.trove4j)

    // Logging
    implementation(libs.logging.slf4j.api)
    implementation(libs.logging.logback.classic)

    // Clikt library for parsing output magics
    implementation(libs.clikt)

    // Http4k for resolving library descriptors
    implementation(libs.bundles.http4k) {
        exclude(group = "org.jetbrains.kotlin")
    }

    // Serialization implementation for kernel code
    implementation(libs.serialization.json)
    implementation(libs.serialization.json5)

    // Test dependencies: kotlin-test and Junit 5
    testImplementation(libs.test.junit.params)
    testImplementation(libs.test.kotlintest.assertions)

    testImplementation(projects.wsServer)
    testImplementation(libs.java.websocket)

    deploy(projects.lib)
    deploy(projects.api)
    deploy(libs.logging.slf4j.api)
    deploy(libs.kotlin.dev.scriptRuntime.get())

    kernelShadowed(projects.kotlinJupyterKernel)

    ideScriptClasspathShadowed(projects.lib) { isTransitive = false }
    ideScriptClasspathShadowed(projects.api) { isTransitive = false }
    ideScriptClasspathShadowed(projects.commonDependencies) {
        excludeStandardKotlinDependencies()
    }
    ideScriptClasspathShadowed(projects.protocolApi) { isTransitive = false }
    ideScriptClasspathShadowed(libs.kotlin.dev.stdlib)
    ideScriptClasspathShadowed(libs.kotlin.dev.stdlibCommon)

    scriptClasspathShadowed.extendsFrom(deploy)
    scriptClasspathShadowed(projects.commonDependencies) {
        excludeStandardKotlinDependencies()
    }
    scriptClasspathShadowed(libs.kotlin.dev.stdlib)

    // Embedded kernel artifact
    embeddableKernel(projects.kotlinJupyterKernel) { isTransitive = false }
    embeddableKernel(libs.kotlin.dev.scriptRuntime) { isTransitive = false }
    addSharedEmbeddedDependenciesTo(embeddableKernel)
}

/**
 * Add shared dependencies between `implementation` and `embeddedKernel` configurations.
 * As we want strict control over dependencies for the embedded kernel, all of these
 * will be added with `transitive = false`, so all dependencies must be explicitly
 * listed here.
 */
private fun DependencyHandler.addSharedEmbeddedDependenciesTo(configuration: Configuration) {
    val configurationName = configuration.name
    listOf(
        libs.kotlin.dev.compilerEmbeddable,
        libs.kotlin.dev.scriptingCompilerImplEmbeddable,
        libs.kotlin.dev.scriptingCompilerEmbeddable,
        libs.kotlin.dev.scriptingIdeServices,
        libs.kotlin.dev.scriptingDependencies,
        libs.kotlin.dev.scriptingDependenciesMavenAll,
        // Embedded version of serialization plugin for notebook code
        libs.serialization.dev.embeddedPlugin,
    ).forEach { dependency ->
        addConfiguredDependencyTo(this, configurationName, dependency) {
            isTransitive = false
        }
    }
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withCompilerArgs {
        requiresOptIn()
        skipPrereleaseCheck()
        samConversionsClass()
        jdkRelease(rootSettings.jvmTarget)
    }
    withTests {
        val doParallelTesting = getFlag("test.parallel", true)

        /**
         *  Set to true to debug classpath/shadowing issues, see testKlaxonClasspathDoesntLeak test
         */
        val useShadowedJar = getFlag("test.useShadowed", false)

        if (useShadowedJar) {
            dependsOn(tasks.shadowJar.get())
            classpath = files(tasks.shadowJar.get()) + classpath
        }

        systemProperties =
            mutableMapOf(
                "junit.jupiter.displayname.generator.default" to "org.junit.jupiter.api.DisplayNameGenerator\$ReplaceUnderscores",
                "junit.jupiter.execution.parallel.enabled" to doParallelTesting.toString() as Any,
                "junit.jupiter.execution.parallel.mode.default" to "concurrent",
                "junit.jupiter.execution.parallel.mode.classes.default" to "concurrent",
            )
    }
}

// Workaround for https://github.com/johnrengelman/shadow/issues/651
components.withType(AdhocComponentWithVariants::class.java).forEach { c ->
    c.withVariantsFromConfiguration(project.configurations.shadowRuntimeElements.get()) {
        skip()
    }
}

tasks {
    register("publishToPluginPortal") {
        group = PUBLISHING_GROUP

        dependsOn(":kotlin-jupyter-api-gradle-plugin:publishPlugins")
    }

    CreateResourcesTask.register(project, "addLibrariesToResources", processResources) {
        addLibrariesFromDir(rootSettings.librariesDir)
    }

    CreateResourcesTask.register(project, "createTestResources", processTestResources) {
        addSingleValueFile("PUBLISHED_JUPYTER_API_VERSION", libs.versions.jupyterApi.get())
    }

    whenTaskAdded {
        val task = this
        if (task is GenerateModuleMetadata && task.name == "generateMetadataFileForKernelPublication") {
            task.dependsOn(shadowJar.get())
        }
    }

    dokkaGeneratePublicationHtml {
        mustRunAfter(shadowJar.get())
    }

    publishDocs {
        docsRepoUrl.set(rootSettings.docsRepo)
        branchName.set("master")
        username.set("robot")
        email.set("robot@jetbrains.com")
    }
}

val kernelShadowedJar =
    tasks.registerShadowJarTasksBy(
        kernelShadowed,
        withSources = false,
        binaryTaskConfigurator = {
            mergeServiceFiles()
            transform(ComponentsXmlResourceTransformer())
            manifest {
                attributes["Implementation-Version"] = project.version
            }
        },
    )
val embeddableKernelJar =
    tasks.registerShadowJarTasksBy(
        embeddableKernel,
        withSources = false,
        binaryTaskConfigurator = CompilerRelocatedJarConfigurator,
    )
val scriptClasspathShadowedJar = tasks.registerShadowJarTasksBy(scriptClasspathShadowed, withSources = true)
val ideScriptClasspathShadowedJar = tasks.registerShadowJarTasksBy(ideScriptClasspathShadowed, withSources = true)

val kernelZip =
    tasks.register("kernelZip", Zip::class) {
        from(deploy)
        include("*.jar")
    }

changelog {
    githubUser = rootSettings.githubRepoUser
    githubToken = rootSettings.githubRepoToken
    githubRepository = rootSettings.githubRepoName
    excludeLabels = setOf("wontfix", "duplicate", "no-changelog", "question")
    customTagByIssueNumber =
        mapOf(
            20 to "0.10.0.183",
            318 to "0.10.0.183",
        )
}

kotlinPublications {
    defaultGroup.set("org.jetbrains.kotlinx")
    defaultArtifactIdPrefix.set("kotlin-jupyter-")
    fairDokkaJars.set(false)

    sonatypeSettings(
        typedProperty("kds.sonatype.central.username"),
        typedProperty("kds.sonatype.central.password"),
        "kotlin-jupyter project, v. ${project.version}",
    )

    signingCredentials(
        typedProperty("kds.sign.key.id"),
        typedProperty("kds.sign.key.private"),
        typedProperty("kds.sign.key.passphrase"),
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

    remoteRepositories {
        maven {
            name = "intellij-deps"
            url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
            credentials {
                username = spaceUsername
                password = spaceToken
            }
        }
    }

    localRepositories {
        localMavenRepository(rootSettings.artifactsDir.resolve("maven"))
    }

    publication {
        publicationName.set("kernel")
        description.set("Kotlin Jupyter kernel published as artifact")
    }

    publication {
        publicationName.set("kernel-shadowed")
        description.set("Kotlin Jupyter kernel with all dependencies inside one artifact")
        composeOfTaskOutputs(kernelShadowedJar)
    }

    publication {
        publicationName.set("embeddable-kernel")
        description.set("Kotlin Kernel suitable for embedding into IntelliJ IDEA")
        composeOfTaskOutputs(embeddableKernelJar)
    }

    publication {
        publicationName.set("script-classpath-shadowed")
        description.set("Kotlin Jupyter kernel script classpath with all dependencies inside one artifact")
        composeOfTaskOutputs(scriptClasspathShadowedJar + kernelZip)
    }

    publication {
        publicationName.set("ide-classpath-shadowed")
        description.set("Kotlin Jupyter kernel script classpath for IDE with all dependencies inside one artifact")
        composeOfTaskOutputs(ideScriptClasspathShadowedJar)
    }
}
