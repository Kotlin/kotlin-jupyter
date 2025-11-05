import build.util.MAVEN_ARTIFACT_NAME_PREFIX
import build.util.addShadowPublications
import build.util.relocatePackages
import com.github.jengelman.gradle.plugins.shadow.transformers.ComponentsXmlResourceTransformer
import org.jetbrains.gradle.shadow.JarTaskOptions
import org.jetbrains.gradle.shadow.registerShadowJarTasksBy

plugins {
    alias(libs.plugins.publisher)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(projects.dependenciesResolution)

    with(project.configurations.implementation.get()) {
        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk7")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
        exclude("org.jetbrains.kotlin", "kotlin-scripting-common")
        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm")
        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-bom")
        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-slf4j")
        exclude("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm")
        exclude("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm")
        exclude("org.slf4j")
    }
}

/**
 * When changing the list, please also adjust the shadowing logic
 * (shadowJar task down below)
 */
val expectedArtifacts =
    listOf(
        rootProject.name + projects.dependenciesResolution.path,
        "org.jetbrains.amper:*",
        "io.opentelemetry:*",
        "io.github.pdvrieze.xmlutil:serialization-jvm",
        "io.github.pdvrieze.xmlutil:core-jvm",
        "org.apache.maven:maven-artifact",
        "org.codehaus.plexus:plexus-utils",
    )

val checkResolverDependencies by tasks.registering {
    doLast {
        val artifacts =
            configurations.runtimeClasspath
                .get()
                .resolvedConfiguration
                .resolvedArtifacts

        val expectedArtifactsSet =
            expectedArtifacts
                .map { it.split(":") }
                .groupBy(keySelector = { it[0] }, valueTransform = { it[1] })
                .mapValues { (_, artifacts) -> if ("*" in artifacts) emptySet() else artifacts.toSet() }

        val artifactIds = artifacts.map { it.moduleVersion.id }

        val unexpectedArtifactIds =
            artifactIds.filterNot { id ->
                when (val expectedArtifactsForGroup = expectedArtifactsSet[id.group]) {
                    null -> false
                    emptySet<String>() -> true
                    else -> id.name in expectedArtifactsForGroup
                }
            }

        require(unexpectedArtifactIds.isEmpty()) {
            buildString {
                appendLine("Some artifacts from the resolver dependency are not expected:")
                for (id in unexpectedArtifactIds) {
                    appendLine(" - $id")
                }
                appendLine("Expected artifacts:")
                for (line in expectedArtifacts) {
                    appendLine(" - $line")
                }
            }
        }
    }
}

tasks.check {
    dependsOn(checkResolverDependencies)
}

buildSettings {
    withCompilerArgs {
        jdkRelease(rootSettings.jvmTarget)
    }
}

/**
 * We need shadowing for the resolver dependency to make
 * linkage errors of different kinds less possible, and the final jars
 * layout of the kernel more predictable.
 */
tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    transform(ComponentsXmlResourceTransformer())
    manifest {
        attributes["Implementation-Version"] = project.version
    }
    relocatePackages {
        +"io.opentelemetry"
        +"kotlinx.io"
        +"nl.adaptivity.xmlutil"
        +"org.apache.maven"
        +"org.codehaus.plexus.util"
        +"org.jetbrains.amper"
    }
}

val docShadowJars =
    tasks.registerShadowJarTasksBy(
        project.configurations.runtimeClasspath.get(),
        project.name,
        binaryOptions = JarTaskOptions.NoJar,
        sourceOptions =
            JarTaskOptions.RegularJar {
                exclude { file ->
                    val firstSegment = file.relativePath.segments.firstOrNull() ?: return@exclude false
                    firstSegment.endsWith("Main") && firstSegment != "jvmMain"
                }
                relocate("jvmMain.", "")
                relocate("javaShared.", "")
                relocate("commonDom.", "")
                relocate("main.", "")
            },
    )

tasks.jar { enabled = false }

kotlinPublications {
    addShadowPublications(
        project = project,
        publicationNameBase = project.name,
        artifactId = MAVEN_ARTIFACT_NAME_PREFIX + project.name,
        description = "Dependencies resolution for Kotlin kernel",
        docShadowJars = docShadowJars,
    )
}
