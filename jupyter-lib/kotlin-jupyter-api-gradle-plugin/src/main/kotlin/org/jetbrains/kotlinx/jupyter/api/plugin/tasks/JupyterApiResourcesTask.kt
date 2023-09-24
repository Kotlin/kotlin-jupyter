package org.jetbrains.kotlinx.jupyter.api.plugin.tasks

import com.google.gson.Gson
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlinx.jupyter.api.plugin.ApiGradlePlugin
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
        val resultObject = LibrariesScanResult(
            definitions = libraryDefinitions.map { FQNAware(it) }.toTypedArray(),
            producers = libraryProducers.map { FQNAware(it) }.toTypedArray(),
        ) + getScanResultFromAnnotations()
        val json = Gson().toJson(resultObject)

        val jupyterDir = outputDir.resolve("META-INF/kotlin-jupyter-libraries")
        val libFile = jupyterDir.resolve("libraries.json")
        libFile.parentFile.mkdirs()
        libFile.writeText(json)
    }

    private fun getScanResultFromAnnotations(): LibrariesScanResult {
        val path = project.getBuildDirectory().resolve(ApiGradlePlugin.FQNS_PATH)

        fun fqns(name: String): Array<FQNAware> {
            val file = path.resolve(name)
            if (!file.exists()) return emptyArray()
            return file.readLines()
                .filter { it.isNotBlank() }
                .map { FQNAware(it) }
                .toTypedArray()
        }

        return LibrariesScanResult(
            fqns("definitions"),
            fqns("producers"),
        )
    }

    data class FQNAware(
        val fqn: String,
    )

    class LibrariesScanResult(
        val definitions: Array<FQNAware>,
        val producers: Array<FQNAware>,
    )

    operator fun LibrariesScanResult.plus(other: LibrariesScanResult): LibrariesScanResult {
        return LibrariesScanResult(
            union(definitions, other.definitions),
            union(producers, other.producers),
        )
    }

    companion object {
        private inline fun <reified T> union(a: Array<T>, b: Array<T>): Array<T> {
            val result = mutableSetOf<T>()
            result.addAll(a)
            result.addAll(b)
            return result.toTypedArray()
        }
    }
}
