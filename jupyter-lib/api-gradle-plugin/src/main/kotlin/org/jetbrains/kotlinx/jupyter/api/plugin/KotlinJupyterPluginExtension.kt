package org.jetbrains.kotlinx.jupyter.api.plugin

import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class KotlinJupyterPluginExtension(
    private val project: Project
) {
    fun addApiDependency(version: String? = null) {
        val apiVersion = version ?: apiVersion()
        project.dependencies {
            "compileOnly"("$GROUP_ID:kotlin-jupyter-api:$apiVersion")
        }
    }

    fun addScannerDependency(version: String? = null) {
        val apiVersion = version ?: apiVersion()
        val mavenCoordinates = "$GROUP_ID:kotlin-jupyter-api-annotations:$apiVersion"
        project.dependencies {
            "implementation"(mavenCoordinates)
            "kapt"(mavenCoordinates)
        }
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