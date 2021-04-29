package org.jetbrains.kotlinx.jupyter.api.plugin

import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import java.util.Locale

private fun Project.configureDependency(scope: String, dependencyNotation: Any) {
    // apply configuration to JVM-only project
    plugins.withId("org.jetbrains.kotlin.jvm") {
        val configuration = project.configurations.findByName(scope)
            ?: error("$scope configuration is not resolved for a Kotlin-JVM project")
        dependencies {
            configuration.invoke(dependencyNotation)
        }
    }
    // apply only to multiplatform plugin
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.findByType<KotlinMultiplatformExtension>()?.apply {
            val jvmTargetName = targets.filterIsInstance<KotlinJvmTarget>().firstOrNull()?.name
                ?: error("Single JVM target not found in a multiplatform project")
            val configuration = project.configurations.findByName(jvmTargetName + scope.capitalize(Locale.ROOT))
                ?: error("$scope configuration is not resolved for a multiplatform project")
            dependencies {
                configuration.invoke(dependencyNotation)
            }
        }
    }
}

class KotlinJupyterPluginExtension(
    private val project: Project
) {
    fun addApiDependency(version: String? = null) = with(project) {
        val apiVersion = version ?: apiVersion()
        configureDependency("compileOnly", "$GROUP_ID:kotlin-jupyter-api:$apiVersion")
    }

    fun addScannerDependency(version: String? = null) = with(project) {
        val kaptConf = configurations.findByName("kapt") ?: return
        val apiVersion = version ?: apiVersion()
        val mavenCoordinates = "$GROUP_ID:kotlin-jupyter-api-annotations:$apiVersion"
        dependencies {
            kaptConf(mavenCoordinates)
        }
        configureDependency("implementation", mavenCoordinates)
    }

    internal fun addDependenciesIfNeeded() {
        if (project.getFlag("kotlin.jupyter.add.api", true)) {
            addApiDependency()
        }
        if (project.getFlag("kotlin.jupyter.add.scanner", true)) {
            addScannerDependency()
        }
    }

    companion object {
        private const val GROUP_ID = "org.jetbrains.kotlinx"

        private fun Project.getFlag(propertyName: String, default: Boolean = false): Boolean {
            return findProperty(propertyName)?.let {
                when (it) {
                    "true", true -> true
                    "false", false -> false
                    else -> null
                }
            } ?: default
        }

        fun apiVersion(): String {
            return ApiGradlePlugin::class.java.classLoader.getResource("VERSION")!!.readText()
        }
    }
}
