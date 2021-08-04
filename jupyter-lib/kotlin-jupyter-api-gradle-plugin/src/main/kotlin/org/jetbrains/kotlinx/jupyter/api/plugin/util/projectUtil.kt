package org.jetbrains.kotlinx.jupyter.api.plugin.util

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.repositories
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import java.util.Locale

internal fun Project.configureDependency(scope: String, dependency: ExternalModuleDependency) {
    repositories {
        if (kotlinVersion().isDevKotlinVersion) {
            addMavenIfDoesNotExist(KOTLIN_DEV_REPOSITORY_NAME, KOTLIN_DEV_REPOSITORY_URL)
        }
    }

    // apply configuration to JVM-only project
    plugins.withId("org.jetbrains.kotlin.jvm") {
        val configuration = project.configurations.findByName(scope)
            ?: error("$scope configuration is not resolved for a Kotlin-JVM project")
        dependencies {
            configuration.invoke(dependency)
        }
    }
    // apply only to multiplatform plugin
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.findByType<KotlinMultiplatformExtension>()?.apply {
            targets.whenAdded(
                { it is KotlinJvmTarget },
                {
                    val jvmTargetName = it.name
                    val configuration = project.configurations.findByName(jvmTargetName + scope.capitalize(Locale.ROOT))
                        ?: error("$scope configuration is not resolved for a multiplatform project")
                    dependencies {
                        configuration.invoke(dependency)
                    }
                }
            )
        }
    }
}

internal fun Project.getFlag(propertyName: String, default: Boolean = false): Boolean {
    return findProperty(propertyName)?.let {
        when (it) {
            "true", true -> true
            "false", false -> false
            else -> null
        }
    } ?: default
}

internal fun Project.propertyByFlag(flagName: String, default: Boolean = false): Property<Boolean> {
    return objects.property<Boolean>().apply { set(provider { getFlag(flagName, default) }) }
}
