package build

import groovy.json.JsonSlurper
import org.jetbrains.kotlinx.jupyter.common.ReplCommand
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import java.io.File

class ReadmeGenerator(
    private val librariesDir: File,
    private val kotlinVersion: String,
    private val repoUrl: String
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
        "magics" to ::processMagics,
        "kotlin_version" to ::processKotlinVersion,
        "repo_url" to ::processRepoUrl
    )

    private fun processSupportedLibraries(): String {
        val libraryFiles =
            librariesDir.listFiles { file -> file.isFile && file.name.endsWith(".json") } ?: emptyArray()

        return libraryFiles.toList().associateTo(sortedMapOf()) { file ->
            val libraryName = file.nameWithoutExtension
            val json = JsonSlurper().parse(file) as Map<*, *>
            val link = json["link"] as? String
            val description = json["description"] as? String

            val namePart = if (link == null) libraryName else "[$libraryName]($link)"
            val descriptionPart = if (description == null) "" else " - $description"
            libraryName to " - $namePart$descriptionPart"
        }.values.joinToString("\n")
    }

    private fun processCommands(): String {
        return ReplCommand.values().joinToString("\n") {
            " - `:${it.nameForUser}` - ${it.desc}"
        }
    }

    private fun processMagics(): String {
        return ReplLineMagic.values().filter { it.visibleInHelp }.joinToString("\n") {
            val description = " - `%${it.nameForUser}` - ${it.desc}."
            val usage = if (it.argumentsUsage == null) ""
            else " Usage example: `%${it.nameForUser} ${it.argumentsUsage}`"

            description + usage
        }
    }

    private fun processKotlinVersion(): String {
        return kotlinVersion
    }

    private fun processRepoUrl(): String {
        return "$repoUrl.git"
    }
}
