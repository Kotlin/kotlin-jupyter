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
    private val enableScannerDependency = project.propertyByFlag("kotlin.jupyter.add.scanner", false)
    private val enableTestKitDependency = project.propertyByFlag("kotlin.jupyter.add.testkit", true)

    val scannerDependencyEnabled get() = enableScannerDependency.get()

    internal fun addDependenciesIfNeeded() {
        if (enableApiDependency.get()) addApiDependency()
        if (enableScannerDependency.get()) addScannerDependency()
        if (enableTestKitDependency.get()) addTestKitDependency()
    }

    @JvmOverloads
    fun addApiDependency(version: String? = null) = with(project) {
        configureDependency("compileOnly", kernelDependency("api", version))
    }

    @JvmOverloads
    fun addScannerDependency(version: String? = null) = with(project) {
        configurations.whenAdded({ it.name == "ksp" }) { kspConf ->
            val annotationsDependency = kernelDependency("api-annotations", version)
            dependencies {
                kspConf(annotationsDependency)
            }
            configureDependency("compileOnly", annotationsDependency)
        }
    }

    @JvmOverloads
    fun addTestKitDependency(version: String? = null) = with(project) {
        configureDependency("testImplementation", kernelDependency("test-kit", version))
    }
}
