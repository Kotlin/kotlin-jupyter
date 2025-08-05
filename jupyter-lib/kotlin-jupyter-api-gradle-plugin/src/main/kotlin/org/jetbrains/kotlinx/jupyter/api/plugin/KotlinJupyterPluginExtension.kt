package org.jetbrains.kotlinx.jupyter.api.plugin

import org.gradle.api.Project
import org.jetbrains.kotlinx.jupyter.api.plugin.util.configureDependency
import org.jetbrains.kotlinx.jupyter.api.plugin.util.kernelDependency
import org.jetbrains.kotlinx.jupyter.api.plugin.util.propertyByFlag

class KotlinJupyterPluginExtension(
    private val project: Project,
) {
    private val enableApiDependency = project.propertyByFlag("kotlin.jupyter.add.api", true)
    private val enableTestKitDependency = project.propertyByFlag("kotlin.jupyter.add.testkit", true)

    internal fun addDependenciesIfNeeded() {
        if (enableApiDependency.get()) addApiDependency()
        if (enableTestKitDependency.get()) addTestKitDependency()
    }

    @JvmOverloads
    fun addApiDependency(version: String? = null) =
        with(project) {
            configureDependency("compileOnly", kernelDependency("api", version))
        }

    @JvmOverloads
    fun addTestKitDependency(version: String? = null) =
        with(project) {
            configureDependency("testImplementation", kernelDependency("test-kit", version))
        }
}
