package org.jetbrains.kotlin.jupyter.build

import groovy.json.JsonSlurper
import org.jetbrains.kotlin.jupyter.common.ReplCommands
import org.jetbrains.kotlin.jupyter.common.ReplLineMagics
import java.io.File

class ReadmeGenerator(
        private val librariesDir: File
) {
    fun generate(stub: File, destination: File) {
        var result = stub.readText()
        for ((stubName, processor) in processors) {
            result = result.replace("[[$stubName]]", processor())
        }
        destination.writeText(result)
    }

    private val processors: Map<String, () -> String> = mapOf(
        "supported_libraries" to ::processSupportedLibraries,
        "supported_commands" to ::processCommands,
        "magics" to ::processMagics
    )

    private fun processSupportedLibraries(): String {
        val libraryFiles =
                librariesDir.listFiles { file -> file.isFile && file.name.endsWith(".json") } ?: emptyArray()

        val sortedMap = sortedMapOf<String, String>()

        return libraryFiles.toList().map { file ->
            val libraryName = file.nameWithoutExtension
            val json = JsonSlurper().parse(file) as Map<*, *>
            val link = json["link"] as? String
            val description = json["description"] as? String

            val namePart = if (link == null) libraryName else "[$libraryName]($link)"
            val descriptionPart = if (description == null) "" else " - $description"
            libraryName to " - $namePart$descriptionPart"
        }.toMap(sortedMap).values.joinToString("\n")
    }

    private fun processCommands(): String {
        return ReplCommands.values().joinToString("\n") {
            " - `:${it.name}` - ${it.desc}"
        }
    }

    private fun processMagics(): String {
        return ReplLineMagics.values().filter { it.visibleInHelp }.joinToString ("\n") {
            val description = " - `%${it.name}` - ${it.desc}"
            val usage = if (it.argumentsUsage == null) ""
            else "\n\tUsage example: %${it.name} ${it.argumentsUsage}"

            description + usage
        }
    }
}
