package org.jetbrains.kotlinx.jupyter.api.plugin

import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

private const val GROUP_ID = "org.jetbrains.kotlinx.jupyter"

private fun Project.getFlag(propertyName: String, default: Boolean = false): Boolean {
    return findProperty(propertyName)?.let {
        when (it) {
            "true", true -> true
            "false", false -> false
            else -> null
        }
    } ?: default
}

fun kotlinJupyterApiVersion(): String {
    return ApiGradlePlugin::class.java.classLoader.getResource("VERSION")!!.readText()
}

fun Project.addKotlinJupyterApiDependency(version: String? = null) {
    val apiVersion = version ?: kotlinJupyterApiVersion()
    dependencies {
        "compileOnly"("$GROUP_ID:kotlin-jupyter-api:$apiVersion")
    }
}

fun Project.addKotlinJupyterScannerDependency(version: String? = null) {
    val apiVersion = version ?: kotlinJupyterApiVersion()
    val mavenCoordinates = "$GROUP_ID:kotlin-jupyter-api-annotations:$apiVersion"
    dependencies {
        "implementation"(mavenCoordinates)
        "kapt"(mavenCoordinates)
    }
}

internal fun Project.addDependenciesIfNeeded() {
    if (getFlag("kotlin.jupyter.add.api", true)) {
        addKotlinJupyterApiDependency()
    }
    if (getFlag("kotlin.jupyter.add.scanner", true)) {
        addKotlinJupyterScannerDependency()
    }
}
