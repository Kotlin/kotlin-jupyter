package org.jetbrains.kotlinx.jupyter.compiler.api
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.util.LinkedSnippet

/**
 * Utility for deserializing compilation results from the CompilerService.
 */
object CompileResultDeserializer {
    /**
     * Deserialize a successful compile result into LinkedSnippet.
     *
     * @param result The successful compilation result containing serialized data
     * @return LinkedSnippet
     * @throws IllegalArgumentException if the result is not a success
     * @throws ClassNotFoundException if deserialization fails due to missing classes
     * @throws java.io.IOException if deserialization fails due to IO errors
     */
    fun deserialize(result: CompileResult.Success): LinkedSnippet<KJvmCompiledScript> {
        // Deserialize the list of compiled scripts
        val scriptsList: List<KJvmCompiledScript> = deserializeObject(result.serializedCompiledSnippet)

        if (scriptsList.isEmpty()) {
            throw IllegalStateException("Deserialized scripts list is empty")
        }

        // Convert list back to LinkedSnippet chain
        return scriptsList.foldRight(null as LinkedSnippet<KJvmCompiledScript>?) { script, prev ->
            object : LinkedSnippet<KJvmCompiledScript> {
                override val previous: LinkedSnippet<KJvmCompiledScript>? = prev
                override fun get(): KJvmCompiledScript = script
            }
        }!!
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> deserializeObject(bytes: ByteArray): T {
        ByteArrayInputStream(bytes).use { bais ->
            ObjectInputStream(bais).use { ois ->
                return ois.readObject() as T
            }
        }
    }
}
