package org.jetbrains.kotlinx.jupyter.api.plugin.tasks

import com.google.gson.Gson
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlinx.jupyter.api.plugin.ApiGradlePlugin
import org.jetbrains.kotlinx.jupyter.api.plugin.KotlinJupyterPluginExtension
import org.jetbrains.kotlinx.jupyter.api.plugin.util.FQNAware
import org.jetbrains.kotlinx.jupyter.api.plugin.util.LibrariesScanResult
import org.jetbrains.kotlinx.jupyter.api.plugin.util.emptyScanResult
import org.jetbrains.kotlinx.jupyter.api.plugin.util.getBuildDirectory
import java.io.File

open class JupyterApiResourcesTask : DefaultTask() {
    /**
     * List of classes/objects implementing
     * `org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer`.
     * Such classes should have exactly one constructor with no arguments or with one `org.jetbrains.kotlinx.jupyter.api.Notebook` argument.
     */
    @Input
    var libraryProducers: List<String> = emptyList()

    /**
     * List of classes/objects implementing
     * `org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition`.
     * Such classes should have exactly one constructor with no arguments or with one `org.jetbrains.kotlinx.jupyter.api.Notebook` argument.
     */
    @Input
    var libraryDefinitions: List<String> = emptyList()

    @OutputDirectory
    val outputDir: File = project.getBuildDirectory().resolve("jupyterProcessedResources")

    @TaskAction
    fun createDescriptions() {
        val jupyterExtension = project.extensions.findByType(KotlinJupyterPluginExtension::class.java)
        val jupyterExtensionScanResult = jupyterExtension?.libraryFqns ?: emptyScanResult

        val taskScanResult =
            LibrariesScanResult(
                definitions = libraryDefinitions.map { FQNAware(it) }.toSet(),
                producers = libraryProducers.map { FQNAware(it) }.toSet(),
            )

        val resultObject =
            jupyterExtensionScanResult +
                taskScanResult +
                getScanResultFromAnnotations()
        val json = Gson().toJson(resultObject)

        val jupyterDir = outputDir.resolve("META-INF/kotlin-jupyter-libraries")
        val libFile = jupyterDir.resolve("libraries.json")
        libFile.parentFile.mkdirs()
        libFile.writeText(json)
    }

    private fun getScanResultFromAnnotations(): LibrariesScanResult {
        val path = project.getBuildDirectory().resolve(ApiGradlePlugin.FQNS_PATH)

        fun fqns(name: String): Set<FQNAware> {
            val file = path.resolve(name)
            if (!file.exists()) return emptySet()
            return file
                .readLines()
                .filter { it.isNotBlank() }
                .map { FQNAware(it) }
                .toSet()
        }

        return LibrariesScanResult(
            fqns("definitions"),
            fqns("producers"),
        )
    }

    operator fun LibrariesScanResult.plus(other: LibrariesScanResult): LibrariesScanResult =
        LibrariesScanResult(
            definitions + other.definitions,
            producers + other.producers,
        )
}
