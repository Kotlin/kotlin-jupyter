package build

import org.gradle.api.Plugin
import org.gradle.api.Project

class KernelBuildPlugin : Plugin<Project> {
    override fun apply(project: Project) {

        with(project.plugins) {
            apply("org.jetbrains.kotlin.jvm")
            apply("com.github.johnrengelman.shadow")
            apply("org.jetbrains.kotlin.plugin.serialization")
            apply("org.jlleitschuh.gradle.ktlint")
            apply("ru.ileasile.kotlin.publisher")
            apply("ru.ileasile.kotlin.doc")
            apply("org.hildan.github.changelog")
        }

        project.allprojects {
            addAllBuildRepositories()
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
