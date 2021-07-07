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
            ${pluginsBlock()}
            
            tasks {
                $JUPYTER_RESOURCES_TASK_NAME {
            $taskSetupIndented
                }
            }
        """.trimIndent()
        buildFile.writeText(buildFileText)
    }

    private fun runResourcesTask(args: Array<String>? = null, type: String = ""): BuildResult {
        val arguments = args?.toMutableList() ?: mutableListOf(
            "-Pkotlin.jupyter.add.api=false",
            "-Pkotlin.jupyter.add.scanner=false",
            "--stacktrace",
            "--info"
        )
        val taskName = RESOURCES_TASK_NAME.withPrefix(type)
        arguments.add(0, taskName)

        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(arguments)
            .forwardOutput()
            .build()
    }

    private fun assertLibrariesJsonContents(expected: LibrariesScanResult, type: String = "") {
        val librariesJsonText = projectDir.resolve(buildLibrariesJsonPath(type)).readText()
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
            ${pluginsBlock()}
            
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
        runResourcesTask()

        assertLibrariesJsonContents(
            LibrariesScanResult(
                producers = listOf("pack.Integration").map(::LibrariesProducerDeclaration)
            )
        )
    }

    @Test
    fun `check annotations in MPP`() {
        val version = ClassLoader.getSystemClassLoader().getResource("VERSION")?.readText().orEmpty()

        val propertiesFile = projectDir.resolve("properties.gradle")
        propertiesFile.writeText(
            """
            kapt.verbose=true
            """.trimIndent()
        )

        val buildFile = projectDir.resolve("build.gradle.kts")

        fun jarFile(name: String): String {
            return File("../$name/build/libs/$name-$version.jar").canonicalFile.absolutePath.replace("\\", "/")
        }

        val apiJar = jarFile("api")
        val apiAnnotations = jarFile("api-annotations")
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "$KOTLIN_VERSION"
                id("org.jetbrains.kotlin.jupyter.api")
            }
            
            kotlin {
                jvm {
                    compilations.all {
                        kotlinOptions.jvmTarget = "11"
                    }
                }
                js(LEGACY) {
                    binaries.executable()
                    browser()
                }
                sourceSets {
                    val commonMain by getting
                    val commonTest by getting
                    val jvmMain by getting {
                        dependencies {
                            implementation(files("$apiJar"))
                            implementation(files("$apiAnnotations"))
                        }
                        dependencies.add("kapt", files("$apiAnnotations"))
                    }
                    val jvmTest by getting
                    val jsMain by getting
                    val jsTest by getting
                }
            }
            """.trimIndent()
        )

        val srcDir = projectDir.resolve("src/jvmMain/kotlin")
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
        runResourcesTask(type = "jvm")

        assertLibrariesJsonContents(
            LibrariesScanResult(
                producers = listOf("pack.Integration").map(::LibrariesProducerDeclaration)
            ),
            "jvm"
        )
    }

    @Test
    fun `check extension`() {
        val version = "0.8.3.202"

        val buildFile = projectDir.resolve("build.gradle")

        buildFile.writeText(
            """
            ${pluginsBlock()}
            
            kotlinJupyter {
                addApiDependency("$version")
                addScannerDependency("$version")
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
        runResourcesTask()

        assertLibrariesJsonContents(
            LibrariesScanResult(
                producers = listOf("pack.Integration").map(::LibrariesProducerDeclaration)
            )
        )
    }

    companion object {
        private const val KOTLIN_VERSION = "1.5.20"
        private const val RESOURCES_TASK_NAME = "processResources"
        private const val JUPYTER_RESOURCES_TASK_NAME = "processJupyterApiResources"

        private fun mainSourceSetBuildResourcesPath(type: String = ""): String {
            return if (type.isEmpty()) {
                "build/resources/main"
            } else {
                "build/processedResources/$type/main"
            }
        }
        private fun buildLibrariesJsonPath(type: String = "") = "${mainSourceSetBuildResourcesPath(type)}/$KOTLIN_JUPYTER_RESOURCES_PATH/$KOTLIN_JUPYTER_LIBRARIES_FILE_NAME"

        private fun pluginsBlock(ktPluginId: String = "jvm") = """
            plugins {
                id 'org.jetbrains.kotlin.$ktPluginId' version '$KOTLIN_VERSION'
                id 'org.jetbrains.kotlin.jupyter.api'
            }
        """.trimIndent()

        private fun String.withPrefix(prefix: String) =
            if (prefix.isEmpty()) this else prefix + capitalize()
    }
}
