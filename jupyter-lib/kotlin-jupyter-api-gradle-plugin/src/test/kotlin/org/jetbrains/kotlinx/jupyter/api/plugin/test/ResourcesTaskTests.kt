package org.jetbrains.kotlinx.jupyter.api.plugin.test

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.jetbrains.kotlinx.jupyter.api.libraries.KOTLIN_JUPYTER_LIBRARIES_FILE_NAME
import org.jetbrains.kotlinx.jupyter.api.libraries.KOTLIN_JUPYTER_RESOURCES_PATH
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesDefinitionDeclaration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesProducerDeclaration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesScanResult
import org.jetbrains.kotlinx.jupyter.api.plugin.KotlinJupyterPluginExtension
import org.junit.jupiter.api.Test
import java.nio.file.Files.createTempDirectory
import java.util.Locale.getDefault
import kotlin.test.assertEquals

class ResourcesTaskTests {
    private val projectDir = createTempDirectory("jupyter-gradle-test-project").toFile()

    private fun setupGroovyTaskTest(taskSetup: String = "") {
        val buildFile = projectDir.resolve("build.gradle")
        val taskSetupIndented = taskSetup.prependIndent("    ".repeat(2))
        val buildFileText =
            """
            ${groovyPluginsBlock()}
            
            tasks {
                $JUPYTER_RESOURCES_TASK_NAME {
            $taskSetupIndented
                }
            }
            """.trimIndent()
        buildFile.writeText(buildFileText)
    }

    private fun setupKotlinExtensionTest(extensionSetup: String = "") {
        val buildFile = projectDir.resolve("build.gradle.kts")
        val extensionSetupIndented = extensionSetup.prependIndent("    ".repeat(2))
        val buildFileText =
            """
            ${kotlinPluginsBlock()}
            
            ${KotlinJupyterPluginExtension.NAME} {
                $extensionSetupIndented
            }
            """.trimIndent()
        buildFile.writeText(buildFileText)
    }

    private fun runResourcesTask(
        args: Array<String>? = null,
        type: String = "",
    ): BuildResult {
        val arguments =
            args?.toMutableList() ?: mutableListOf(
                "-Pkotlin.jupyter.add.api=false",
                "--stacktrace",
                "--info",
            )
        val taskName = RESOURCES_TASK_NAME.withPrefix(type)
        arguments.add(0, taskName)

        return GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(arguments)
            .forwardOutput()
            .build()
    }

    private fun assertLibrariesJsonContents(
        expected: LibrariesScanResult,
        type: String = "",
    ) {
        val librariesJsonText = projectDir.resolve(buildLibrariesJsonPath(type)).readText()
        val libraryInfo = Json.decodeFromString<LibrariesScanResult>(serializer(), librariesJsonText)

        assertEquals(expected, libraryInfo)
    }

    @Test
    fun `check that serialization is correct`() {
        setupGroovyTaskTest(
            """
            libraryProducers = ["test.Producer1", "test.Producer2"]
            libraryDefinitions = ["test.Definition1"]
            """.trimIndent(),
        )
        runResourcesTask()

        assertLibrariesJsonContents(
            LibrariesScanResult(
                producers = listOf("test.Producer1", "test.Producer2").map(::LibrariesProducerDeclaration),
                definitions = listOf("test.Definition1").map(::LibrariesDefinitionDeclaration),
            ),
        )
    }

    @Test
    fun `check that empty lists are handled correctly`() {
        setupGroovyTaskTest(
            """
            libraryDefinitions = ["test.Definition1"]
            """.trimIndent(),
        )
        runResourcesTask()

        assertLibrariesJsonContents(
            LibrariesScanResult(
                definitions = listOf("test.Definition1").map(::LibrariesDefinitionDeclaration),
            ),
        )
    }

    @Test
    fun `libraries defined in extension are added`() {
        setupKotlinExtensionTest(
            """
            integrations {
                definition("test.Definition3")
                producer("test.Producer3")
            }
            """.trimIndent(),
        )
        runResourcesTask()
        assertLibrariesJsonContents(
            LibrariesScanResult(
                definitions = listOf("test.Definition3").map(::LibrariesDefinitionDeclaration),
                producers = listOf("test.Producer3").map(::LibrariesProducerDeclaration),
            ),
        )
    }

    companion object {
        private const val KOTLIN_VERSION = "1.6.0"
        private const val RESOURCES_TASK_NAME = "processResources"
        private const val JUPYTER_RESOURCES_TASK_NAME = "processJupyterApiResources"

        private fun mainSourceSetBuildResourcesPath(type: String = ""): String =
            if (type.isEmpty()) {
                "build/resources/main"
            } else {
                "build/processedResources/$type/main"
            }

        private fun buildLibrariesJsonPath(type: String = "") =
            "${mainSourceSetBuildResourcesPath(type)}/$KOTLIN_JUPYTER_RESOURCES_PATH/$KOTLIN_JUPYTER_LIBRARIES_FILE_NAME"

        private fun groovyPluginsBlock(ktPluginId: String = "jvm") =
            """
            plugins {
                id 'org.jetbrains.kotlin.$ktPluginId' version '$KOTLIN_VERSION'
                id 'org.jetbrains.kotlin.jupyter.api'
            }
            """.trimIndent()

        private fun kotlinPluginsBlock(ktPluginId: String = "jvm") =
            """
            plugins {
                id("org.jetbrains.kotlin.$ktPluginId") version "$KOTLIN_VERSION"
                id("org.jetbrains.kotlin.jupyter.api")
            }
            """.trimIndent()

        private fun String.withPrefix(prefix: String): String =
            if (prefix.isEmpty()) {
                this
            } else {
                prefix +
                    replaceFirstChar {
                        if (it.isLowerCase()) {
                            it.titlecase(getDefault())
                        } else {
                            it.toString()
                        }
                    }
            }
    }
}
