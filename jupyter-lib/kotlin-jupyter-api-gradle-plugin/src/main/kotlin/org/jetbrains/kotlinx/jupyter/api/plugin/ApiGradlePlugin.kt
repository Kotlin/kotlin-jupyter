package org.jetbrains.kotlinx.jupyter.api.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.repositories
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlinx.jupyter.api.plugin.tasks.JupyterApiResourcesTask
import org.jetbrains.kotlinx.jupyter.api.plugin.util.addMavenCentralIfDoesNotExist
import org.jetbrains.kotlinx.jupyter.api.plugin.util.whenAdded

class ApiGradlePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.run {
            apply(Kapt3GradleSubplugin::class.java)
        }

        val jupyterBuildPath = buildDir.resolve(FQNS_PATH)
        extensions.configure<KaptExtension>("kapt") {
            arguments {
                arg("kotlin.jupyter.fqn.path", jupyterBuildPath)
            }
        }

        repositories {
            addMavenCentralIfDoesNotExist()
        }

        val pluginExtension = KotlinJupyterPluginExtension(target)
        extensions.add("kotlinJupyter", pluginExtension)
        pluginExtension.addDependenciesIfNeeded()

        tasks {
            val cleanJupyterTask = register("cleanJupyterPluginFiles") {
                doLast {
                    jupyterBuildPath.deleteRecursively()
                }
            }

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

            fun dependOnKapt(kaptTaskName: String) {
                registerResourceTask()
                tasks.whenAdded(
                    { it.name == kaptTaskName },
                    {
                        tasks.named(resourcesTaskName) {
                            dependsOn(it)
                            it.dependsOn(cleanJupyterTask)
                            it.outputs.dir(jupyterBuildPath)
                        }
                    }
                )
            }

            // apply configuration to JVM-only project
            plugins.withId("org.jetbrains.kotlin.jvm") {
                dependOnProcessingTask("processResources")
                dependOnKapt("kaptKotlin")
            }

            // apply only to multiplatform plugin
            plugins.withId("org.jetbrains.kotlin.multiplatform") {
                extensions.findByType<KotlinMultiplatformExtension>()?.apply {
                    targets.whenAdded(
                        { (it is KotlinJvmTarget) },
                        {
                            dependOnProcessingTask(it.name + "ProcessResources")
                        }
                    )
                }
                dependOnKapt("kaptKotlinJvm")
            }
        }
    }

    companion object {
        const val FQNS_PATH = "generated/jupyter/fqns"
    }
}
