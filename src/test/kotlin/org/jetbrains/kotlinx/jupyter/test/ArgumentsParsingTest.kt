package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSameSizeAs
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.parseCommandLine
import org.jetbrains.kotlinx.jupyter.startup.parameters.KotlinKernelOwnParams
import org.jetbrains.kotlinx.jupyter.startup.parameters.KotlinKernelOwnParamsBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.reflect.full.memberProperties

class ArgumentsParsingTest {
    @Test
    fun `own parameters builder should align with the corresponding class`() {
        val boundParametersProperty = KotlinKernelOwnParamsBuilder::boundParameters
        val builderProperties =
            KotlinKernelOwnParamsBuilder::class
                .memberProperties
                .filter { it != boundParametersProperty }

        val classProperties = KotlinKernelOwnParams::class.memberProperties
        builderProperties shouldBeSameSizeAs classProperties

        val classPropertiesMap = classProperties.associateBy { it.name }
        for (builderProperty in builderProperties) {
            classPropertiesMap.shouldContainKey(builderProperty.name)
        }
    }

    @Test
    fun `should parse config file path`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)
        val args = parseCommandLine(configFile.absolutePath)
        args.cfgFile.absolutePath shouldBe configFile.absolutePath
    }

    @Test
    fun `should parse classpath parameter`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)

        val path1 = "/path/to/lib1.jar"
        val path2 = "/path/to/lib2.jar"
        val classpath = "$path1${File.pathSeparator}$path2"

        val args = parseCommandLine("-cp=$classpath", configFile.absolutePath)
        args.ownParams.scriptClasspath.map { it.path } shouldContainExactly listOf(path1, path2)
    }

    @Test
    fun `should parse home directory parameter`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)
        val homeDir = "/path/to/home"
        val args = parseCommandLine("-home=$homeDir", configFile.absolutePath)
        args.ownParams.homeDir?.path shouldBe homeDir
    }

    @Test
    fun `should parse debug port parameter`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)
        val debugPort = 5005
        val args = parseCommandLine("-debugPort=$debugPort", configFile.absolutePath)
        args.ownParams.debugPort shouldBe debugPort
    }

    @Test
    fun `should parse client type parameter`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)
        val clientType = "jupyter"
        val args = parseCommandLine("-client=$clientType", configFile.absolutePath)
        args.ownParams.clientType shouldBe clientType
    }

    @Test
    fun `should parse JVM target parameter`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)
        val jvmTarget = "11"
        val args = parseCommandLine("-jvmTarget=$jvmTarget", configFile.absolutePath)
        args.ownParams.jvmTargetForSnippets shouldBe jvmTarget
    }

    @Test
    fun `should parse REPL compiler mode parameter`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)
        val args = parseCommandLine("-replCompilerMode=K2", configFile.absolutePath)
        args.ownParams.replCompilerMode shouldBe K2
    }

    @Test
    fun `should parse extra compiler arguments parameter`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)
        val extraArgs = "arg1,arg2,arg3"
        val args = parseCommandLine("-extraCompilerArgs=$extraArgs", configFile.absolutePath)
        args.ownParams.extraCompilerArguments shouldContainExactly listOf("arg1", "arg2", "arg3")
    }

    @Test
    fun `should parse multiple parameters together`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)

        val debugPort = 5005
        val clientType = "jupyter"
        val jvmTarget = "11"

        val args =
            parseCommandLine(
                "-debugPort=$debugPort",
                "-client=$clientType",
                "-jvmTarget=$jvmTarget",
                configFile.absolutePath,
            )

        args.ownParams.debugPort shouldBe debugPort
        args.ownParams.clientType shouldBe clientType
        args.ownParams.jvmTargetForSnippets shouldBe jvmTarget
    }

    @Test
    fun `should generate args list from KernelArgs`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)

        val debugPort = 5005
        val clientType = "jupyter"

        val args =
            parseCommandLine(
                "-debugPort=$debugPort",
                "-client=$clientType",
                configFile.absolutePath,
            )

        val argsList = args.argsList()

        // Parse the generated args list back to verify it's correct
        val parsedArgs = parseCommandLine(*argsList.toTypedArray())

        parsedArgs.cfgFile.absolutePath shouldBe configFile.absolutePath
        parsedArgs.ownParams.debugPort shouldBe debugPort
        parsedArgs.ownParams.clientType shouldBe clientType
    }

    @Test
    fun `should throw exception for missing config file`() {
        val exception =
            assertThrows<IllegalArgumentException>("Should throw when config file is missing") {
                parseCommandLine("-debugPort=5005")
            }
        exception.message shouldBe "config file is not provided"
    }

    @Test
    fun `should throw exception for non-existent config file`() {
        val nonExistentFile = "/non/existent/file.json"
        val exception =
            assertThrows<IllegalArgumentException>("Should throw for non-existent config file") {
                parseCommandLine(nonExistentFile)
            }
        exception.message shouldBe "invalid config file $nonExistentFile"
    }

    @Test
    fun `should throw exception for unrecognized argument`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)
        val exception =
            assertThrows<IllegalArgumentException>("Should throw for unrecognized argument") {
                parseCommandLine("-unknownArg=value", configFile.absolutePath)
            }
        exception.message shouldBe "Unrecognized argument: -unknownArg=value"
    }

    @Test
    fun `should throw exception for invalid debug port value`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)
        val exception =
            assertThrows<IllegalArgumentException>("Should throw for invalid debug port") {
                parseCommandLine("-debugPort=not-a-number", configFile.absolutePath)
            }
        exception.message shouldBe "Argument should be integer: not-a-number"
    }

    @Test
    fun `should throw exception for invalid REPL compiler mode value`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)
        val exception =
            assertThrows<IllegalArgumentException>("Should throw for invalid REPL compiler mode") {
                parseCommandLine("-replCompilerMode=INVALID_MODE", configFile.absolutePath)
            }
        exception.message shouldBe "Invalid replCompilerMode: INVALID_MODE"
    }

    @Test
    fun `should handle empty classpath`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)
        val args = parseCommandLine("-cp=", configFile.absolutePath)
        args.ownParams.scriptClasspath.map { it.path } shouldBe listOf("")
    }

    @Test
    fun `should handle empty extra compiler arguments`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)
        val args = parseCommandLine("-extraCompilerArgs=", configFile.absolutePath)
        args.ownParams.extraCompilerArguments.shouldBeEmpty()
    }

    @Test
    fun `should generate args list with all parameters`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)

        val debugPort = 5005
        val clientType = "jupyter"
        val jvmTarget = "11"
        val extraArgs = "arg1,arg2"
        val path1 = "/path/to/lib1.jar"
        val path2 = "/path/to/lib2.jar"
        val classpath = "$path1${File.pathSeparator}$path2"
        val homeDir = "/path/to/home"

        val args =
            parseCommandLine(
                "-debugPort=$debugPort",
                "-client=$clientType",
                "-jvmTarget=$jvmTarget",
                "-replCompilerMode=K1",
                "-extraCompilerArgs=$extraArgs",
                "-cp=$classpath",
                "-home=$homeDir",
                configFile.absolutePath,
            )

        val argsList = args.argsList()

        // Parse the generated args list back to verify it's correct
        val parsedArgs = parseCommandLine(*argsList.toTypedArray())

        parsedArgs.cfgFile.absolutePath shouldBe configFile.absolutePath
        parsedArgs.ownParams.debugPort shouldBe debugPort
        parsedArgs.ownParams.clientType shouldBe clientType
        parsedArgs.ownParams.jvmTargetForSnippets shouldBe jvmTarget
        parsedArgs.ownParams.replCompilerMode shouldBe K1
        parsedArgs.ownParams.extraCompilerArguments shouldContainExactly listOf("arg1", "arg2")
        parsedArgs.ownParams.scriptClasspath.map { it.path } shouldContainExactly listOf(path1, path2)
        parsedArgs.ownParams.homeDir?.path shouldBe homeDir
    }

    @Test
    fun `should throw exception when setting extra compiler args twice`(
        @TempDir tempDir: Path,
    ) {
        val configFile = createConfigFile(tempDir)

        val exception =
            assertThrows<IllegalArgumentException>("Should throw when setting extra compiler args twice") {
                parseCommandLine(
                    "-extraCompilerArgs=arg1,arg2",
                    "-extraCompilerArgs=arg3,arg4",
                    configFile.absolutePath,
                )
            }

        exception.message shouldBe "Extra compiler args were already set to [arg1, arg2]"
    }

    @Test
    fun `should throw exception when setting config file twice`(
        @TempDir tempDir: Path,
    ) {
        val configFile1 = createConfigFile(tempDir, "config1.json")
        val configFile2 = createConfigFile(tempDir, "config2.json")

        val exception =
            assertThrows<IllegalArgumentException>("Should throw when setting config file twice") {
                parseCommandLine(configFile1.absolutePath, configFile2.absolutePath)
            }

        exception.message shouldBe "config file already set to ${configFile1.absolutePath}"
    }

    private fun createConfigFile(
        tempDir: Path,
        name: String = "config.json",
    ): File =
        File(tempDir.toFile(), name).apply {
            writeText("{}")
            deleteOnExit()
        }
}
