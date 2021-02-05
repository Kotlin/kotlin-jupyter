package org.jetbrains.kotlinx.jupyter.api.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.maven
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

        target.extensions.configure<KaptExtension>("kapt") {
            arguments {
                arg("kotlin.jupyter.fqn.path", target.buildDir.resolve(FQNS_PATH))
            }
        }

        target.repositories {
            mavenCentral()
            maven("https://kotlin.bintray.com/kotlin-datascience")
        }

        target.addDependenciesIfNeeded()

        target.tasks {
            val resourcesTaskName = "processJupyterApiResources"
            register<JupyterApiResourcesTask>(resourcesTaskName) {
                dependsOn("kaptKotlin")
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
