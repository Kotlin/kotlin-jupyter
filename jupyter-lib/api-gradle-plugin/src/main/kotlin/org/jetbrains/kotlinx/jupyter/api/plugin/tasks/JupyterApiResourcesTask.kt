package org.jetbrains.kotlinx.jupyter.api.plugin.tasks

import com.google.gson.Gson
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.lang.IllegalStateException

open class JupyterApiResourcesTask : DefaultTask() {
    @Input
    var libraryProducers: List<String> = emptyList()

    @Input
    var libraryDefinitions: List<String> = emptyList()

    @OutputDirectory
    val outputDir: File

    init {
        val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer
        val mainSourceSet = sourceSets.named("main").get()
        outputDir = mainSourceSet.output.resourcesDir?.resolve("META-INF/kotlin-jupyter-libraries")
            ?: throw IllegalStateException("No resources dir for main source set")
    }

    @TaskAction
    fun createDescriptions() {
        val resultObject = LibrariesScanResult(
            definitions = libraryDefinitions.map { FQNAware(it) }.toTypedArray(),
            producers = libraryProducers.map { FQNAware(it) }.toTypedArray()
        )
        val json = Gson().toJson(resultObject)

        val libFile = outputDir.resolve("libraries.json")
        libFile.writeText(json)
    }

    class FQNAware(
        val fqn: String
    )

    class LibrariesScanResult(
        val definitions: Array<FQNAware>,
        val producers: Array<FQNAware>
    )
}
