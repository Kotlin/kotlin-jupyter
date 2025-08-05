package org.jetbrains.kotlinx.jupyter.api.plugin.util

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.repositories
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import java.io.File

@OptIn(ExperimentalKotlinGradlePluginApi::class)
internal fun Project.configureDependency(
    scope: String,
    dependency: ExternalModuleDependency,
) {
    repositories {
        if (kotlinVersion().isDevKotlinVersion) {
            addMavenIfDoesNotExist(KOTLIN_DEV_REPOSITORY_NAME, KOTLIN_DEV_REPOSITORY_URL)
            addMavenIfDoesNotExist(KOTLIN_BOOTSTRAP_REPOSITORY_NAME, KOTLIN_BOOTSTRAP_REPOSITORY_URL)
        }
    }

    // apply configuration to JVM-only project
    plugins.withId("org.jetbrains.kotlin.jvm") {
        val configuration =
            project.configurations.findByName(scope)
                ?: error("$scope configuration is not resolved for a Kotlin-JVM project. ${allConfigurationsNamesMessage()}")
        dependencies {
            configuration.invoke(dependency)
        }
    }
    // apply only to multiplatform plugin
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.findByType<KotlinMultiplatformExtension>()?.apply {
            targets.whenAdded(
                { it is KotlinJvmTarget },
                { target ->
                    sourceSets.addDependency(scope, target.name, dependency)
                },
            )
        }
    }
}

private fun NamedDomainObjectContainer<KotlinSourceSet>.addDependency(
    scope: String,
    targetName: String,
    dependency: ExternalModuleDependency,
) {
    val isTestScope = scope.startsWith("test")
    val cleanScopeName =
        if (isTestScope) {
            scope.removePrefix("test").replaceFirstChar { it.lowercase() }
        } else {
            scope
        }

    val sourceSetName = targetName + if (isTestScope) "Test" else "Main"

    named(sourceSetName) {
        dependencies {
            when (cleanScopeName) {
                "implementation" -> implementation(dependency)
                "api" -> api(dependency)
                "runtimeOnly" -> runtimeOnly(dependency)
                "compileOnly" -> compileOnly(dependency)
                else -> error("Unknown scope: $cleanScopeName")
            }
        }
    }
}

private fun Project.allConfigurationsNamesMessage(): String = "All available configurations: ${configurations.names.joinToString(", ")}."

internal fun Project.getFlag(
    propertyName: String,
    default: Boolean = false,
): Boolean =
    findProperty(propertyName)?.let {
        when (it) {
            "true", true -> true
            "false", false -> false
            else -> null
        }
    } ?: default

internal fun Project.propertyByFlag(
    flagName: String,
    default: Boolean = false,
): Property<Boolean> = objects.property<Boolean>().apply { set(provider { getFlag(flagName, default) }) }

fun Project.getBuildDirectory(): File = layout.buildDirectory.get().asFile
