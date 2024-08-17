package build

import build.util.defaultVersionCatalog
import build.util.devKotlin
import build.util.exampleKernel
import build.util.taskTempFile
import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.tooling.BuildException
import org.jetbrains.kotlinx.jupyter.common.ReplCommand
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import java.io.File

class ReadmeGenerator(
    private val project: Project,
    private val settings: RootSettingsExtension,
) {
    fun registerTasks(configuration: Task.() -> Unit) {
        fun Task.defineInputs() {
            inputs.file(settings.readmeStubFile)
            inputs.dir(settings.librariesDir)
            inputs.property("kotlinVersion", project.defaultVersionCatalog.versions.devKotlin)
            inputs.property("projectRepoUrl", settings.projectRepoUrl)
        }

        project.tasks.register(GENERATE_README_TASK) {
            group = BUILD_GROUP

            defineInputs()
            outputs.file(settings.readmeFile)

            doLast {
                generateTo(settings.readmeFile)
            }

            configuration()
        }

        project.tasks.register(CHECK_README_TASK) {
            group = VERIFICATION_GROUP

            defineInputs()
            inputs.file(settings.readmeFile)

            doLast {
                val tempFile = taskTempFile("generatedReadme.md")
                generateTo(tempFile)
                if (tempFile.readText() != settings.readmeFile.readText()) {
                    throw BuildException("Readme is not regenerated. Regenerate it using `./gradlew $GENERATE_README_TASK` command", null)
                }
            }

            configuration()
        }

        project.tasks.named(CHECK_TASK) {
            if (!settings.skipReadmeCheck) {
                dependsOn(CHECK_README_TASK)
            }
        }
    }

    private fun generateTo(destination: File) {
        var result = settings.readmeStubFile.readText()
        for ((stubName, processor) in processors) {
            result = result.replace("[[$stubName]]", processor())
        }
        destination.parentFile.mkdirs()
        destination.writeText(result)
    }

    private val processors: Map<String, () -> String> = mapOf(
        "supported_libraries" to ::processSupportedLibraries,
        "supported_commands" to ::processCommands,
        "magics" to ::processMagics,
        "kotlin_version" to ::processKotlinVersion,
        "kernel_version" to ::processKernelVersion,
        "repo_url" to ::processRepoUrl
    )

    private fun processSupportedLibraries(): String {
        val libraryFiles =
            settings.librariesDir.listFiles { file -> file.isFile && file.name.endsWith(".json") } ?: emptyArray()

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
        return ReplCommand.values().joinToString(
            "\n",
            tableHeader(listOf("Command", "Description"))
        ) {
            tableRow(listOf("`:${it.nameForUser}`", it.desc))
        }
    }

    private fun processMagics(): String {
        return ReplLineMagic.values().filter { it.visibleInHelp }.joinToString(
            "\n",
            tableHeader(listOf("Magic", "Description", "Usage example"))
        ) {
            val magicName = "`%${it.nameForUser}`"
            val description = it.desc
            val usage = if (it.argumentsUsage == null) ""
            else "`%${it.nameForUser} ${it.argumentsUsage}`"

            tableRow(listOf(magicName, description, usage))
        }
    }

    private fun processKotlinVersion(): String {
        return project.defaultVersionCatalog.versions.devKotlin
    }

    private fun processKernelVersion(): String {
        return project.defaultVersionCatalog.versions.exampleKernel
    }

    private fun processRepoUrl(): String {
        return "${settings.projectRepoUrl}.git"
    }

    private fun tableHeader(titles: List<String>): String {
        return """
            
            ${tableRow(titles)}
            ${titles.joinToString("|", "|", "|") {
                ":" + "-".repeat(it.length + 1)
            }}
            
        """.trimIndent()
    }

    private fun tableRow(contents: List<String>): String {
        return contents.joinToString(" | ", "| ", " |")
    }
}
