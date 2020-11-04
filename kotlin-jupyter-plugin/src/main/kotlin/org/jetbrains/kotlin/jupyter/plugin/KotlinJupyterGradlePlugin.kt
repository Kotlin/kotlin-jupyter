package org.jetbrains.kotlin.jupyter.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.jupyter.build.ProjectWithOptionsImpl
import org.jetbrains.kotlin.jupyter.build.createCleanTasks
import org.jetbrains.kotlin.jupyter.build.createInstallTasks
import org.jetbrains.kotlin.jupyter.build.prepareCondaTasks
import org.jetbrains.kotlin.jupyter.build.prepareDistributionTasks
import org.jetbrains.kotlin.jupyter.build.prepareKotlinVersionUpdateTask
import org.jetbrains.kotlin.jupyter.build.prepareLocalTasks
import org.jetbrains.kotlin.jupyter.build.preparePropertiesTask
import org.jetbrains.kotlin.jupyter.build.preparePyPiTasks
import org.jetbrains.kotlin.jupyter.build.prepareReadmeTasks

class KotlinJupyterGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(ProjectWithOptionsImpl(project, project.options())) {
            /****** Helper tasks ******/
            prepareReadmeTasks()
            prepareKotlinVersionUpdateTask()

            /****** Build tasks ******/
            preparePropertiesTask()
            createCleanTasks()

            /****** Local install ******/
            prepareLocalTasks()

            /****** Distribution ******/
            prepareDistributionTasks()
            createInstallTasks(false, distribBuildPath.resolve(distribKernelDir), distribBuildPath.resolve(runKernelDir))
            prepareCondaTasks()
            preparePyPiTasks()
        }
    }
}
