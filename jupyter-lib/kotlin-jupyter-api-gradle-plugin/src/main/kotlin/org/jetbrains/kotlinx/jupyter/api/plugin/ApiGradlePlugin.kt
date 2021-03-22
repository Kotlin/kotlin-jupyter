package org.jetbrains.kotlinx.jupyter.api.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.repositories
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlinx.jupyter.api.plugin.tasks.JupyterApiResourcesTask

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
            mavenCentral()
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
            fun registerResourceTask() {
                register<JupyterApiResourcesTask>(resourcesTaskName) {
                    val kaptKotlinTask = findByName("kaptKotlin")
                    if (kaptKotlinTask != null) {
                        dependsOn(kaptKotlinTask)
                        kaptKotlinTask.dependsOn(cleanJupyterTask)
                        kaptKotlinTask.outputs.dir(jupyterBuildPath)
                    }
                }
            }

            // apply configuration to JVM-only project
            plugins.withId("org.jetbrains.kotlin.jvm") {
                // Task should be registered after plugin is applied
                registerResourceTask()
                named("processResources") {
                    dependsOn(resourcesTaskName)
                }
            }

            // apply only to multiplatform plugin
            plugins.withId("org.jetbrains.kotlin.multiplatform") {
                // Task should be registered after plugin is applied
                registerResourceTask()
                extensions.findByType<KotlinMultiplatformExtension>()?.apply {
                    val jvmTargetName = targets.filterIsInstance<KotlinJvmTarget>().firstOrNull()?.name
                        ?: error("Single JVM target not found in a multiplatform project")
                    named(jvmTargetName + "ProcessResources") {
                        dependsOn(resourcesTaskName)
                    }
                }
            }
        }
    }

    companion object {
        const val FQNS_PATH = "generated/jupyter/fqns"
    }
}
