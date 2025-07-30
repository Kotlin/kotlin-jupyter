package org.jetbrains.kotlinx.jupyter.startup.parameters

import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.NamedKernelParameter
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.SimpleNamedKernelIntParameter
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.SimpleNamedKernelStringParameter
import java.io.File

/**
 * Parameter handler for the classpath entries.
 * Accepts multiple path entries separated by the platform-specific path separator.
 */
val scriptClasspathParameter =
    object : NamedKernelParameter<List<File>>(
        aliases = listOf("cp", "classpath"),
    ) {
        override fun parseValue(
            argValue: String,
            previousValue: List<File>?,
        ): List<File> = argValue.split(File.pathSeparator).map { File(it) }

        override fun serializeValue(value: List<File>): String? {
            if (value.isEmpty()) return null
            return value.joinToString(File.pathSeparator) { it.absolutePath }
        }
    }

/**
 * Parameter handler for the kernel home directory.
 * Specifies the base directory where the kernel will look for resources and configurations.
 */
val homeDirParameter =
    object : NamedKernelParameter<File>(
        aliases = listOf("home"),
    ) {
        override fun parseValue(
            argValue: String,
            previousValue: File?,
        ): File = File(argValue)

        override fun serializeValue(value: File): String = value.absolutePath
    }

/**
 * Parameter handler for the debug port.
 * Specifies the port number to use for remote debugging of the kernel.
 */
val debugPortParameter = SimpleNamedKernelIntParameter("debugPort")

/**
 * Parameter handler for the client type.
 * Specifies the type of client that is connecting to the kernel.
 */
val clientTypeParameter = SimpleNamedKernelStringParameter("client")

/**
 * Parameter handler for the JVM target version.
 * Specifies the target JVM version for compiled snippets (e.g., "1.8", "11", "17").
 */
val jvmTargetParameter = SimpleNamedKernelStringParameter("jvmTarget")

/**
 * Parameter handler for the REPL compiler mode.
 * Specifies the compilation mode to use for the REPL.
 */
val replCompilerModeParameter =
    object : NamedKernelParameter<ReplCompilerMode>(
        aliases = listOf("replCompilerMode"),
    ) {
        override fun parseValue(
            argValue: String,
            previousValue: ReplCompilerMode?,
        ): ReplCompilerMode =
            ReplCompilerMode.entries.find {
                it.name == argValue
            } ?: throw IllegalArgumentException("Invalid replCompilerMode: $argValue")

        override fun serializeValue(value: ReplCompilerMode): String = value.name
    }

/**
 * Parameter handler for additional compiler arguments.
 * Specifies extra arguments to pass to the Kotlin compiler when compiling snippets.
 * Can't be specified twice in the command line.
 */
val extraCompilerArgumentsParameter =
    object : NamedKernelParameter<List<String>>(
        aliases = listOf("extraCompilerArgs"),
    ) {
        private val separator = ","

        override fun parseValue(
            argValue: String,
            previousValue: List<String>?,
        ): List<String> {
            if (previousValue != null) {
                throw IllegalArgumentException("Extra compiler args were already set to $previousValue")
            }
            return argValue
                .split(separator)
                .filter { it.isNotBlank() }
        }

        override fun serializeValue(value: List<String>): String? {
            if (value.isEmpty()) return null
            return value.joinToString(separator)
        }
    }
