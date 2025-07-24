package org.jetbrains.kotlinx.jupyter.api.plugin

import org.gradle.api.Project
import org.jetbrains.kotlinx.jupyter.api.plugin.util.FQNAware
import org.jetbrains.kotlinx.jupyter.api.plugin.util.LibrariesScanResult
import org.jetbrains.kotlinx.jupyter.api.plugin.util.configureDependency
import org.jetbrains.kotlinx.jupyter.api.plugin.util.kernelDependency
import org.jetbrains.kotlinx.jupyter.api.plugin.util.propertyByFlag

class KotlinJupyterPluginExtension(
    private val project: Project,
) {
    private val enableApiDependency = project.propertyByFlag("kotlin.jupyter.add.api", true)
    private val enableTestKitDependency = project.propertyByFlag("kotlin.jupyter.add.testkit", true)

    private val libraryProducers: MutableSet<FQNAware> = mutableSetOf()
    private val libraryDefinitions: MutableSet<FQNAware> = mutableSetOf()

    internal val libraryFqns get() =
        LibrariesScanResult(
            definitions = libraryDefinitions,
            producers = libraryProducers,
        )

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

    /**
     * Add adding library integrations by specifying their fully qualified names
     */
    fun integrations(action: IntegrationsSpec.() -> Unit) {
        IntegrationsSpec().apply(action)
    }

    inner class IntegrationsSpec {
        @Suppress("unused")
        fun producer(className: String) {
            libraryProducers.add(FQNAware(className))
        }

        @Suppress("unused")
        fun definition(className: String) {
            libraryDefinitions.add(FQNAware(className))
        }
    }

    companion object {
        internal const val NAME: String = "kotlinJupyter"
    }
}
