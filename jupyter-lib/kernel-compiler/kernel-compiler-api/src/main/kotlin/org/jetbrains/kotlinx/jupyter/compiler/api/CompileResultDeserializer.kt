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
     * @param result The successful compilation result containing serialized data and hash codes
     * @param cache Optional cache to retrieve and store deserialized scripts by their hash code.
     *              If provided, scripts already in the cache will be reused instead of deserializing.
     * @return LinkedSnippet
     * @throws IllegalArgumentException if the hash codes list size doesn't match the scripts list size
     * @throws ClassNotFoundException if deserialization fails due to missing classes
     * @throws java.io.IOException if deserialization fails due to IO errors
     */
    fun deserialize(
        result: CompileResult.Success,
        cache: MutableMap<Int, KJvmCompiledScript>?,
    ): LinkedSnippet<KJvmCompiledScript> {
        require(result.scriptHashCodes.isNotEmpty()) { "Hash codes list is empty" }

        val deserializedScripts by lazy {
            deserializeObject<List<KJvmCompiledScript>>(result.serializedCompiledSnippet).also {
                require(it.size == result.scriptHashCodes.size) {
                    "Deserialized scripts list size (${it.size}) doesn't match hash codes list size (${result.scriptHashCodes.size})"
                }
            }
        }

        // Convert list back to LinkedSnippet chain
        var snippet: LinkedSnippet<KJvmCompiledScript>? = null
        for ((index, hashCode) in result.scriptHashCodes.withIndex()) {
            val script = cache?.getOrPut(hashCode) { deserializedScripts[index] } ?: deserializedScripts[index]
            snippet =
                object : LinkedSnippet<KJvmCompiledScript> {
                    override val previous: LinkedSnippet<KJvmCompiledScript>? = snippet

                    override fun get(): KJvmCompiledScript = script
                }
        }
        return snippet!! // since result.scriptHashCodes is not empty
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
