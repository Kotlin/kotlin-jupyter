package org.jetbrains.kotlinx.jupyter.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlinx.jupyter.build.ProjectWithOptionsImpl
import org.jetbrains.kotlinx.jupyter.build.createCleanTasks
import org.jetbrains.kotlinx.jupyter.build.createInstallTasks
import org.jetbrains.kotlinx.jupyter.build.prepareAggregateUploadTasks
import org.jetbrains.kotlinx.jupyter.build.prepareCondaTasks
import org.jetbrains.kotlinx.jupyter.build.prepareDistributionTasks
import org.jetbrains.kotlinx.jupyter.build.prepareKotlinVersionUpdateTask
import org.jetbrains.kotlinx.jupyter.build.prepareLocalTasks
import org.jetbrains.kotlinx.jupyter.build.preparePropertiesTask
import org.jetbrains.kotlinx.jupyter.build.preparePyPiTasks
import org.jetbrains.kotlinx.jupyter.build.prepareReadmeTasks

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
            prepareAggregateUploadTasks()
        }
    }
}
