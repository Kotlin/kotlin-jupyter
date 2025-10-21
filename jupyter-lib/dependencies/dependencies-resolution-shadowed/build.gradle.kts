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
        exclude("org.jetbrains.kotlin", "kotlin-scripting-common")
        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm")
        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-bom")
        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-slf4j")
        exclude("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm")
        exclude("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm")
        exclude("org.slf4j")
    }
}

buildSettings {
    withCompilerArgs {
        jdkRelease(rootSettings.jvmTarget)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    transform(ComponentsXmlResourceTransformer())
    manifest {
        attributes["Implementation-Version"] = project.version
    }
    relocatePackages {
        +"io.ktor"
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
