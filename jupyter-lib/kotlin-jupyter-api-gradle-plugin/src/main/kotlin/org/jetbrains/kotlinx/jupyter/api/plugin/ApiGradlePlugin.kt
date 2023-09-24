package org.jetbrains.kotlinx.jupyter.api.plugin

import com.google.devtools.ksp.gradle.KspExtension
import com.google.devtools.ksp.gradle.KspGradleSubplugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.repositories
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlinx.jupyter.api.plugin.tasks.JupyterApiResourcesTask
import org.jetbrains.kotlinx.jupyter.api.plugin.util.addMavenCentralIfDoesNotExist
import org.jetbrains.kotlinx.jupyter.api.plugin.util.getBuildDirectory
import org.jetbrains.kotlinx.jupyter.api.plugin.util.whenAdded

class ApiGradlePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        val pluginExtension = KotlinJupyterPluginExtension(target)
        extensions.add("kotlinJupyter", pluginExtension)

        val jupyterBuildPath = getBuildDirectory().resolve(FQNS_PATH)
        jupyterBuildPath.mkdirs()
        if (pluginExtension.scannerDependencyEnabled) {
            pluginManager.run {
                apply(KspGradleSubplugin::class.java)
            }
        }
        configurations.whenAdded({ it.name == "ksp" }) {
            extensions.configure<KspExtension>("ksp") {
                arg("kotlin.jupyter.fqn.path", jupyterBuildPath.absolutePath)
            }
        }
        pluginExtension.addDependenciesIfNeeded()

        repositories {
            addMavenCentralIfDoesNotExist()
        }

        tasks {
            val resourcesTaskName = "processJupyterApiResources"
            fun registerResourceTask(): JupyterApiResourcesTask {
                findByName(resourcesTaskName) ?: register<JupyterApiResourcesTask>(resourcesTaskName)
                return named<JupyterApiResourcesTask>(resourcesTaskName).get()
            }

            fun dependOnProcessingTask(processTaskName: String) {
                val jupyterTask = registerResourceTask()
                tasks.named<Copy>(processTaskName) {
                    dependsOn(resourcesTaskName)
                    from(jupyterTask.outputDir)
                }
            }

            fun dependOnKsp(kspTaskName: String) {
                val jupyterTask = registerResourceTask()
                tasks.whenAdded(
                    { it.name == kspTaskName },
                    { kspTask ->
                        jupyterTask.dependsOn(kspTask)
                        tasks.named(resourcesTaskName) {
                            dependsOn(kspTask)
                            kspTask.outputs.dir(jupyterBuildPath)
                        }
                    },
                )
            }

            // apply configuration to JVM-only project
            plugins.withId("org.jetbrains.kotlin.jvm") {
                dependOnProcessingTask("processResources")
                dependOnKsp("kspKotlin")
            }

            // apply only to multiplatform plugin
            plugins.withId("org.jetbrains.kotlin.multiplatform") {
                extensions.findByType<KotlinMultiplatformExtension>()?.apply {
                    targets.whenAdded(
                        { (it is KotlinJvmTarget) },
                        {
                            dependOnProcessingTask(it.name + "ProcessResources")
                        },
                    )
                }
                dependOnKsp("kspKotlinJvm")
            }
        }
    }

    companion object {
        const val FQNS_PATH = "generated/jupyter/fqns"
    }
}
