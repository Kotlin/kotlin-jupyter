package org.jetbrains.kotlinx.jupyter.api.plugin.util

import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.jetbrains.kotlinx.jupyter.api.plugin.ApiGradlePlugin
import java.net.URI

private const val GROUP_ID = "org.jetbrains.kotlinx"
internal const val KOTLIN_DEV_REPOSITORY_NAME = "Kotlin Dev repo"
internal const val KOTLIN_DEV_REPOSITORY_URL = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev"
internal const val KOTLIN_BOOTSTRAP_REPOSITORY_NAME = "Kotlin Bootstrap repo"
internal const val KOTLIN_BOOTSTRAP_REPOSITORY_URL = "https://redirector.kotlinlang.org/maven/bootstrap"

internal val String.isDevKotlinVersion: Boolean get() = "-" in this

internal fun RepositoryHandler.addMavenIfDoesNotExist(
    name: String,
    url: String,
): ArtifactRepository =
    findByName(name) ?: maven {
        this.name = name
        this.url = URI(url)
    }

internal fun RepositoryHandler.addMavenCentralIfDoesNotExist(): ArtifactRepository =
    addMavenIfDoesNotExist(
        ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME,
        ArtifactRepositoryContainer.MAVEN_CENTRAL_URL,
    )

private fun readFileProp(fileName: String): String =
    ApiGradlePlugin::class.java.classLoader
        .getResource(fileName)!!
        .readText()

internal fun kernelVersion(): String = readFileProp("VERSION")

internal fun kotlinVersion(): String = readFileProp("KOTLIN_VERSION")

internal fun kernelDependency(
    moduleName: String,
    version: String? = null,
): ExternalModuleDependency = DefaultExternalModuleDependency(GROUP_ID, "kotlin-jupyter-$moduleName", version ?: kernelVersion())
