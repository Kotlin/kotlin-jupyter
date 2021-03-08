package org.jetbrains.kotlinx.jupyter.api.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.repositories
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlinx.jupyter.api.plugin.tasks.JupyterApiResourcesTask

class ApiGradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.run {
            apply(Kapt3GradleSubplugin::class.java)
        }

        val jupyterBuildPath = target.buildDir.resolve(FQNS_PATH)
        target.extensions.configure<KaptExtension>("kapt") {
            arguments {
                arg("kotlin.jupyter.fqn.path", jupyterBuildPath)
            }
        }

        target.repositories {
            mavenCentral()
        }

        val pluginExtension = KotlinJupyterPluginExtension(target)
        target.extensions.add("kotlinJupyter", pluginExtension)
        pluginExtension.addDependenciesIfNeeded()

        target.tasks {
            val cleanJupyterTask = register("cleanJupyterPluginFiles") {
                doLast {
                    jupyterBuildPath.deleteRecursively()
                }
            }

            val resourcesTaskName = "processJupyterApiResources"
            register<JupyterApiResourcesTask>(resourcesTaskName) {
                val kaptKotlinTask = findByName("kaptKotlin")
                if (kaptKotlinTask != null) {
                    dependsOn(kaptKotlinTask)
                    kaptKotlinTask.dependsOn(cleanJupyterTask)
                }
            }

            named("processResources") {
                dependsOn(resourcesTaskName)
            }
        }
    }

    companion object {
        const val FQNS_PATH = "generated/jupyter/fqns"
    }
}
