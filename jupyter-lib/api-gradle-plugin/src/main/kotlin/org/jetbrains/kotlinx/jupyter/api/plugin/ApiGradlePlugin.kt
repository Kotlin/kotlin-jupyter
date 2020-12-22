package org.jetbrains.kotlinx.jupyter.api.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlinx.jupyter.api.plugin.tasks.JupyterApiResourcesTask

class ApiGradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks {
            val resourcesTaskName = "processJupyterApiResources"
            register<JupyterApiResourcesTask>(resourcesTaskName)

            named("processResources") {
                dependsOn(resourcesTaskName)
            }
        }
    }
}
