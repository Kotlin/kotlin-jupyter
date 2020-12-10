package org.jetbrains.kotlin.jupyter.compiler

import org.jetbrains.kotlin.jupyter.compiler.util.SerializedCompiledScript
import org.jetbrains.kotlin.jupyter.compiler.util.SerializedCompiledScriptsData
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
                    SerializedCompiledScript(name, encoder.encodeToString(bytes))
                } else null
            }
        )
    }

    private fun deserialize(data: SerializedCompiledScriptsData): Sequence<Pair<String, ByteArray>> {
        return sequence {
            for (script in data.scripts) {
                yield(script.fileName to decoder.decode(script.data))
            }
        }
    }

    fun deserializeAndSave(data: SerializedCompiledScriptsData, outputDir: Path): List<String> {
        val classNames = mutableListOf<String>()
        deserialize(data).forEach { (name, bytes) ->
            val file = outputDir.resolve(name).toFile()
            classNames.add(file.nameWithoutExtension)
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
