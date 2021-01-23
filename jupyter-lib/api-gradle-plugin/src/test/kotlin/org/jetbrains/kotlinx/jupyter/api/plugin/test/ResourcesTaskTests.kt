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
import java.io.File
import java.nio.file.Files.createTempDirectory
import kotlin.test.assertEquals

class ResourcesTaskTests {
    private val projectDir = createTempDirectory("jupyter-gradle-test-project").toFile()

    private fun setupTest(
        taskSetup: String = ""
    ) {
        val buildFile = projectDir.resolve("build.gradle")
        val taskSetupIndented = taskSetup.prependIndent("    ".repeat(2))
        val buildFileText = """
            $PLUGINS_BLOCK
            
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

    @Test
    fun `check annotations`() {
        val version = ClassLoader.getSystemClassLoader().getResource("VERSION")?.readText().orEmpty()

        val propertiesFile = projectDir.resolve("properties.gradle")
        propertiesFile.writeText(
            """
            kapt.verbose=true
            """.trimIndent()
        )

        val buildFile = projectDir.resolve("build.gradle")

        fun jarFile(name: String): String {
            return File("../$name/build/libs/$name-$version.jar").canonicalFile.absolutePath.replace("\\", "/")
        }

        val apiJar = jarFile("api")
        val apiAnnotations = jarFile("api-annotations")
        buildFile.writeText(
            """
            $PLUGINS_BLOCK
            
            dependencies {
                implementation(files("$apiJar"))
                implementation(files("$apiAnnotations"))
                kapt(files("$apiAnnotations"))
            }
            """.trimIndent()
        )

        val srcDir = projectDir.resolve("src/main/kotlin")
        srcDir.mkdirs()

        val integrationKt = srcDir.resolve("pack").resolve("Integration.kt")
        integrationKt.parentFile.mkdirs()
        integrationKt.writeText(
            """
            package pack
            
            import org.jetbrains.kotlinx.jupyter.api.annotations.JupyterLibrary
            import org.jetbrains.kotlinx.jupyter.api.*
            import org.jetbrains.kotlinx.jupyter.api.libraries.*
            
            @JupyterLibrary
            class Integration : JupyterIntegration({            
                import("org.my.lib.*")
            })
            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("processJupyterApiResources")
            .forwardOutput()
            .build()

        assertLibrariesJsonContents(
            LibrariesScanResult(
                producers = listOf("pack.Integration").map(::LibrariesProducerDeclaration)
            )
        )
    }

    companion object {
        private const val RESOURCES_TASK_NAME = "processResources"
        private const val JUPYTER_RESOURCES_TASK_NAME = "processJupyterApiResources"

        private const val MAIN_SOURCE_SET_BUILD_RESOURCES_PATH = "build/resources/main"
        private const val BUILD_LIBRARIES_JSON_PATH = "$MAIN_SOURCE_SET_BUILD_RESOURCES_PATH/$KOTLIN_JUPYTER_RESOURCES_PATH/$KOTLIN_JUPYTER_LIBRARIES_FILE_NAME"

        private val PLUGINS_BLOCK = """
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '1.4.20'
                id 'org.jetbrains.kotlinx.jupyter.api.plugin'
            }
        """.trimIndent()
    }
}
