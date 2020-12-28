package org.jetbrains.kotlinx.jupyter.api.plugin.test

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.jetbrains.kotlinx.jupyter.api.libraries.KOTLIN_JUPYTER_LIBRARIES_FILE_NAME
import org.jetbrains.kotlinx.jupyter.api.libraries.KOTLIN_JUPYTER_RESOURCES_PATH
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesDefinitionDeclaration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesProducerDeclaration
import org.jetbrains.kotlinx.jupyter.api.libraries.LibrariesScanResult
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ResourcesTaskTests {
    private val projectDir = createTempDir("jupyter-gradle-test-project")

    private fun setupTest(
        taskSetup: String = ""
    ) {
        val buildFile = projectDir.resolve("build.gradle")
        val taskSetupIndented = taskSetup.prependIndent("    ".repeat(2))
        val buildFileText = """
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '1.4.20'
                id 'org.jetbrains.kotlinx.jupyter.api.plugin'
            }
            
            tasks {
                $JUPYTER_RESOURCES_TASK_NAME {
            $taskSetupIndented
                }
            }
        """.trimIndent()
        buildFile.writeText(buildFileText)
    }

    private fun runResourcesTask(): BuildResult {
        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(RESOURCES_TASK_NAME)
            .build()
    }

    private fun assertLibrariesJsonContents(expected: LibrariesScanResult) {
        val librariesJsonText = projectDir.resolve(BUILD_LIBRARIES_JSON_PATH).readText()
        val libraryInfo = Json.decodeFromString<LibrariesScanResult>(librariesJsonText)

        assertEquals(expected, libraryInfo)
    }

    @Test
    fun `check that serialization is correct`() {
        setupTest(
            """
            libraryProducers = ["test.Producer1", "test.Producer2"]
            libraryDefinitions = ["test.Definition1"]
            """.trimIndent()
        )
        runResourcesTask()

        assertLibrariesJsonContents(
            LibrariesScanResult(
                producers = listOf("test.Producer1", "test.Producer2").map(::LibrariesProducerDeclaration),
                definitions = listOf("test.Definition1").map(::LibrariesDefinitionDeclaration)
            )
        )
    }

    @Test
    fun `check that empty lists are handled correctly`() {
        setupTest(
            """
            libraryDefinitions = ["test.Definition1"]
            """.trimIndent()
        )
        runResourcesTask()

        assertLibrariesJsonContents(
            LibrariesScanResult(
                definitions = listOf("test.Definition1").map(::LibrariesDefinitionDeclaration)
            )
        )
    }

    companion object {
        private const val RESOURCES_TASK_NAME = "processResources"
        private const val JUPYTER_RESOURCES_TASK_NAME = "processJupyterApiResources"

        private const val MAIN_SOURCE_SET_BUILD_RESOURCES_PATH = "build/resources/main"
        private const val BUILD_LIBRARIES_JSON_PATH = "$MAIN_SOURCE_SET_BUILD_RESOURCES_PATH/$KOTLIN_JUPYTER_RESOURCES_PATH/$KOTLIN_JUPYTER_LIBRARIES_FILE_NAME"
    }
}
