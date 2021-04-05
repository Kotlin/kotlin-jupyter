package org.jetbrains.kotlinx.jupyter.compiler

import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScript
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScriptsData
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.Base64
import kotlin.script.experimental.jvm.impl.KJvmCompiledModuleInMemory
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript

class CompiledScriptsSerializer {
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun serialize(script: KJvmCompiledScript): SerializedCompiledScriptsData {
        val moduleInMemory = script.getCompiledModule() as KJvmCompiledModuleInMemory

        return SerializedCompiledScriptsData(
            moduleInMemory.compilerOutputFiles.mapNotNullTo(mutableListOf()) { (name, bytes) ->
                if (name.endsWith(".class")) {
                    SerializedCompiledScript(
                        name,
                        encoder.encodeToString(bytes),
                        !name.contains("$"),
                    )
                } else null
            }
        )
    }

    private fun deserialize(data: SerializedCompiledScriptsData): Sequence<Pair<SerializedCompiledScript, ByteArray>> {
        return sequence {
            for (script in data.scripts) {
                yield(script to decoder.decode(script.data))
            }
        }
    }

    /**
     * Deserializes [data] containing information about compiled scripts, saves
     * it to the [outputDir] directory, returns the list of names of classes
     * which are meant to be implicit receivers.
     */
    fun deserializeAndSave(data: SerializedCompiledScriptsData, outputDir: Path): List<String> {
        val classNames = mutableListOf<String>()
        deserialize(data).forEach { (script, bytes) ->
            val file = outputDir.resolve(script.fileName).toFile()
            if (script.isImplicitReceiver) {
                classNames.add(file.nameWithoutExtension)
            }
            FileOutputStream(file).use { fos ->
                BufferedOutputStream(fos).use { out ->
                    out.write(bytes)
                    out.flush()
                }
            }
        }
        return classNames
    }
}
