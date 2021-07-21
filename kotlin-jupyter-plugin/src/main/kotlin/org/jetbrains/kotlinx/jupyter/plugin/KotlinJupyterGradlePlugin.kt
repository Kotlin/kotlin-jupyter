package org.jetbrains.kotlinx.jupyter.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlinx.jupyter.build.ProjectWithOptionsImpl
import org.jetbrains.kotlinx.jupyter.build.createCleanTasks
import org.jetbrains.kotlinx.jupyter.build.createInstallTasks
import org.jetbrains.kotlinx.jupyter.build.prepareAggregateUploadTasks
import org.jetbrains.kotlinx.jupyter.build.prepareCondaTasks
import org.jetbrains.kotlinx.jupyter.build.prepareDistributionTasks
import org.jetbrains.kotlinx.jupyter.build.prepareKotlinVersionUpdateTasks
import org.jetbrains.kotlinx.jupyter.build.prepareLocalTasks
import org.jetbrains.kotlinx.jupyter.build.preparePropertiesTask
import org.jetbrains.kotlinx.jupyter.build.preparePyPiTasks
import org.jetbrains.kotlinx.jupyter.build.prepareReadmeTasks

class KotlinJupyterGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {

        with(project.plugins) {
            apply("org.jetbrains.kotlin.jvm")
            apply("com.github.johnrengelman.shadow")
            apply("org.jetbrains.kotlin.plugin.serialization")
            apply("org.jlleitschuh.gradle.ktlint")
            apply("org.jetbrains.kotlinx.jupyter.dependencies")
            apply("ru.ileasile.kotlin.publisher")
            apply("ru.ileasile.kotlin.doc")
            apply("org.hildan.github.changelog")
        }

        with(ProjectWithOptionsImpl(project, project.options())) {
            /****** Helper tasks ******/
            prepareReadmeTasks()
            prepareKotlinVersionUpdateTasks()

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
