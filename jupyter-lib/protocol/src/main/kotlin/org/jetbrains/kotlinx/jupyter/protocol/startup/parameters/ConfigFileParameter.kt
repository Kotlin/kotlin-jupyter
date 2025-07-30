package org.jetbrains.kotlinx.jupyter.protocol.startup.parameters

import java.io.File

/**
 * Parameter handler for the configuration file path.
 * This is a positional parameter (not prefixed with a name) that specifies the path to the configuration file.
 */
val configFileParameter =
    object : KernelParameter<File> {
        override fun tryParse(
            arg: String,
            previousValue: File?,
        ): File? {
            // Config file is a positional parameter, not a named one.
            // Moreover, now it might be in ANY position.
            // Consider converting it into a named one when another parameter with similar semantics is introduced
            if (arg.startsWith("-")) return null
            if (previousValue != null) {
                throw IllegalArgumentException("config file already set to $previousValue")
            }
            return File(arg)
        }

        override fun serialize(value: File): String = value.absolutePath
    }
