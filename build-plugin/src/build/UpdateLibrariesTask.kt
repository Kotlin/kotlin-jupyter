package build

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.property
import java.io.File

abstract class UpdateLibrariesTask : DefaultTask() {
    @get:Input
    val latestCommitHash: Property<String> = project.objects.property<String>().apply {
        set(project.provider { BUILD_LIBRARIES.latestCommitOnDefaultBranch })
    }

    @get:OutputDirectory
    val librariesDir: Property<File> = project.objects.property<File>().apply {
        set(BUILD_LIBRARIES.localLibrariesDir)
    }

    @TaskAction
    fun update() {
        val latestSha = latestCommitHash.get()
        if (BUILD_LIBRARIES.checkIfRefUpToDate(latestSha)) return
        BUILD_LIBRARIES.downloadLibraries(latestSha)
    }

    companion object {
        fun getOrCreate(project: Project): UpdateLibrariesTask {
            val task = project.tasks.findByName(UPDATE_LIBRARIES_TASK_NAME)
            if (task != null) return task as UpdateLibrariesTask
            return project.tasks.create<UpdateLibrariesTask>(UPDATE_LIBRARIES_TASK_NAME)
        }
    }
}
