package org.jetbrains.kotlin.jupyter.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.jupyter.build.prepareKotlinVersionUpdateTask

class KotlinJupyterBuildDependency: Plugin<Project> {
    override fun apply(project: Project) {
        project.prepareKotlinVersionUpdateTask()
    }
}