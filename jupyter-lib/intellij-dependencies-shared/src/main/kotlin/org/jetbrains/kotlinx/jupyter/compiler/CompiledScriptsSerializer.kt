package org.jetbrains.kotlinx.jupyter.compiler

import org.jetbrains.kotlinx.jupyter.repl.SerializedScriptSource
import org.jetbrains.kotlinx.jupyter.repl.result.SerializedCompiledScript
import org.jetbrains.kotlinx.jupyter.repl.result.SerializedCompiledScriptsData
import org.jetbrains.kotlinx.jupyter.repl.result.buildScriptsData
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.Base64
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.impl.KJvmCompiledModuleInMemory
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript

class CompiledScriptsSerializer {
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun serialize(
        script: KJvmCompiledScript,
        source: SourceCode,
    ): SerializedCompiledScriptsData {
        val moduleInMemory = script.getCompiledModule() as KJvmCompiledModuleInMemory

        return buildScriptsData {
            moduleInMemory.compilerOutputFiles.forEach { (name, bytes) ->
                if (name.endsWith(".class")) {
                    addCompiledScript(
                        SerializedCompiledScript(
                            name,
                            encoder.encodeToString(bytes),
                            !name.contains("$"),
                        ),
                    )
                }
            }

            addSource(
                SerializedScriptSource(
                    source.name!! + ".kts",
                    source.text,
                ),
            )
        }
    }

    private fun deserializeCompiledScripts(data: SerializedCompiledScriptsData): Sequence<Pair<SerializedCompiledScript, ByteArray>> =
        sequence {
            for (script in data.scripts) {
                yield(script to decoder.decode(script.data))
            }
        }

    /**
     * Deserializes [data] containing information about compiled scripts, saves
     * it to the [scriptsDir] directory, returns the list of names of classes
     * which are meant to be implicit receivers. Saves script sources to [sourcesDir].
     */
    fun deserializeAndSave(
        data: SerializedCompiledScriptsData,
        scriptsDir: Path,
        sourcesDir: Path,
    ): List<String> {
        val classNames = mutableListOf<String>()
        deserializeCompiledScripts(data).forEach { (script, bytes) ->
            val file = scriptsDir.resolve(script.fileName).toFile()
            file.parentFile.mkdirs()
            if (script.isImplicitReceiver) {
                val classFqn =
                    script.fileName
                        .removeSuffix(".class")
                        .replace('/', '.')
                classNames.add(classFqn)
            }
            FileOutputStream(file).use { fos ->
                BufferedOutputStream(fos).use { out ->
                    out.write(bytes)
                    out.flush()
                }
            }
        }
        data.sources.forEach { scriptSource ->
            val file = sourcesDir.resolve(scriptSource.fileName).toFile()
            file.parentFile.mkdirs()
            file.writeText(scriptSource.text)
        }
        return classNames
    }
}
