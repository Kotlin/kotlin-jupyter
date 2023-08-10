import build.CreateResourcesTask
import build.PUBLISHING_GROUP
import build.util.composeOfTaskOutputs
import build.util.excludeStandardKotlinDependencies
import build.util.getFlag
import build.util.registerShadowJarTasksBy
import build.util.typedProperty
import org.jetbrains.kotlinx.publisher.apache2
import org.jetbrains.kotlinx.publisher.developer
import org.jetbrains.kotlinx.publisher.githubRepo

plugins {
    id("build.plugins.main")
}

val deploy: Configuration by configurations.creating

val kernelShadowed: Configuration by configurations.creating
val scriptClasspathShadowed: Configuration by configurations.creating
val ideScriptClasspathShadowed: Configuration by configurations.creating

ktlint {
    filter {
        exclude("**/org/jetbrains/kotlinx/jupyter/repl.kt")
    }
}

dependencies {
    // Dependency on module with compiler.
    api(projects.sharedCompiler)

    // Standard dependencies
    implementation(libs.kotlin.dev.reflect)
    implementation(libs.jetbrains.annotations)
    implementation(libs.coroutines.core)

    // Embedded compiler and scripting dependencies
    implementation(libs.kotlin.dev.compilerEmbeddable)
    implementation(libs.kotlin.dev.scriptingCompilerImplEmbeddable)
    implementation(libs.kotlin.dev.scriptingCompilerEmbeddable)
    implementation(libs.kotlin.dev.scriptingIdeServices)
    implementation(libs.kotlin.dev.scriptingDependenciesMavenAll)
    implementation(libs.kotlin.dev.scriptingCommon)
    implementation(libs.kotlin.dev.scriptingJvm)

    // Embedded version of serialization plugin for notebook code
    implementation(libs.serialization.dev.embeddedPlugin)

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

    deploy(projects.lib)
    deploy(projects.api)
    deploy(libs.kotlin.dev.scriptRuntime.get())

    kernelShadowed(projects.kotlinJupyterKernel)

    ideScriptClasspathShadowed(projects.lib) { isTransitive = false }
    ideScriptClasspathShadowed(projects.api) { isTransitive = false }
    ideScriptClasspathShadowed(projects.commonDependencies) {
        excludeStandardKotlinDependencies()
    }
    ideScriptClasspathShadowed(libs.kotlin.dev.stdlib)
    ideScriptClasspathShadowed(libs.kotlin.dev.stdlibCommon)

    scriptClasspathShadowed(projects.lib)
    scriptClasspathShadowed(projects.api)
    scriptClasspathShadowed(projects.commonDependencies) {
        excludeStandardKotlinDependencies()
    }
    scriptClasspathShadowed(libs.kotlin.dev.stdlib)
    scriptClasspathShadowed(libs.kotlin.dev.scriptRuntime)
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withCompilerArgs {
        requiresOptIn()
        skipPrereleaseCheck()
        samConversionsClass()
    }
    withTests()
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

    test {
        val doParallelTesting = getFlag("test.parallel", true)
        maxHeapSize = "3072m"

        /**
         *  Set to true to debug classpath/shadowing issues, see testKlaxonClasspathDoesntLeak test
         */
        val useShadowedJar = getFlag("test.useShadowed", false)

        if (useShadowedJar) {
            dependsOn(shadowJar.get())
            classpath = files(shadowJar.get()) + classpath
        }

        systemProperties = mutableMapOf(
            "junit.jupiter.displayname.generator.default" to "org.junit.jupiter.api.DisplayNameGenerator\$ReplaceUnderscores",

            "junit.jupiter.execution.parallel.enabled" to doParallelTesting.toString() as Any,
            "junit.jupiter.execution.parallel.mode.default" to "concurrent",
            "junit.jupiter.execution.parallel.mode.classes.default" to "concurrent",
        )
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

    dokkaHtmlMultiModule {
        mustRunAfter(shadowJar.get())
    }

    publishDocs {
        docsRepoUrl.set(rootSettings.docsRepo)
        branchName.set("master")
        username.set("robot")
        email.set("robot@jetbrains.com")
    }
}

val kernelShadowedJar = tasks.registerShadowJarTasksBy(kernelShadowed, withSources = false)
val scriptClasspathShadowedJar = tasks.registerShadowJarTasksBy(scriptClasspathShadowed, withSources = true)
val ideScriptClasspathShadowedJar = tasks.registerShadowJarTasksBy(ideScriptClasspathShadowed, withSources = false)

changelog {
    githubUser = rootSettings.githubRepoUser
    githubRepository = rootSettings.githubRepoName
    excludeLabels = listOf("wontfix", "duplicate", "no-changelog", "question")
    customTagByIssueNumber = mapOf(
        20 to "0.10.0.183",
        318 to "0.10.0.183",
    )
}

kotlinPublications {
    defaultGroup.set("org.jetbrains.kotlinx")
    defaultArtifactIdPrefix.set("kotlin-jupyter-")
    fairDokkaJars.set(false)

    sonatypeSettings(
        typedProperty("kds.sonatype.user"),
        typedProperty("kds.sonatype.password"),
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
        publicationName.set("script-classpath-shadowed")
        description.set("Kotlin Jupyter kernel script classpath with all dependencies inside one artifact")
        composeOfTaskOutputs(scriptClasspathShadowedJar)
    }

    publication {
        publicationName.set("ide-classpath-shadowed")
        description.set("Kotlin Jupyter kernel script classpath for IDE with all dependencies inside one artifact")
        composeOfTaskOutputs(ideScriptClasspathShadowedJar)
    }
}
