package org.jetbrains.kotlinx.jupyter.api.plugin

import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlinx.jupyter.api.plugin.util.configureDependency
import org.jetbrains.kotlinx.jupyter.api.plugin.util.kernelDependency
import org.jetbrains.kotlinx.jupyter.api.plugin.util.propertyByFlag
import org.jetbrains.kotlinx.jupyter.api.plugin.util.whenAdded

class KotlinJupyterPluginExtension(
    private val project: Project
) {
    private val enableApiDependency = project.propertyByFlag("kotlin.jupyter.add.api", true)
    private val enableScannerDependency = project.propertyByFlag("kotlin.jupyter.add.scanner", true)
    private val enableTestKitDependency = project.propertyByFlag("kotlin.jupyter.add.testkit", true)

    internal fun addDependenciesIfNeeded() {
        if (enableApiDependency.get()) addApiDependency()
        if (enableScannerDependency.get()) addScannerDependency()
        if (enableTestKitDependency.get()) addTestKitDependency()
    }

    fun addApiDependency(version: String? = null) = with(project) {
        configureDependency("compileOnly", kernelDependency("api", version))
    }

    fun addScannerDependency(version: String? = null) = with(project) {
        configurations.whenAdded({ it.name == "kapt" }) { kaptConf ->
            val annotationsDependency = kernelDependency("api-annotations", version)
            dependencies {
                kaptConf(annotationsDependency)
            }
            configureDependency("implementation", annotationsDependency)
        }
    }

    fun addTestKitDependency(version: String? = null) = with(project) {
        configureDependency("testImplementation", kernelDependency("test-kit", version))
    }
}
