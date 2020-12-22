package org.jetbrains.kotlinx.jupyter.api.plugin.tasks

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlinx.jupyter.api.libraries.KOTLIN_JUPYTER_LIBRARIES_FILE_NAME
import org.jetbrains.kotlinx.jupyter.api.libraries.KOTLIN_JUPYTER_RESOURCES_PATH
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesDefinitionDeclaration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesProducerDeclaration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesScanResult
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
        outputDir = mainSourceSet.output.resourcesDir?.resolve(KOTLIN_JUPYTER_RESOURCES_PATH)
            ?: throw IllegalStateException("No resources dir for main source set")
    }

    @TaskAction
    fun createDescriptions() {
        val resultObject = LibrariesScanResult(
            definitions = libraryDefinitions.map { LibrariesDefinitionDeclaration(it) },
            producers = libraryProducers.map { LibrariesProducerDeclaration(it) }
        )
        val json = Json.encodeToString(resultObject)

        val libFile = outputDir.resolve(KOTLIN_JUPYTER_LIBRARIES_FILE_NAME)
        libFile.writeText(json)
    }
}
