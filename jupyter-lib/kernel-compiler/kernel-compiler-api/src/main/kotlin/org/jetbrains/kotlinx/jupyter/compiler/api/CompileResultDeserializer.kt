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
     * Optional [cache] is used to retrieve and store deserialized scripts by their hash code.
     * If provided, scripts already in the cache will be reused instead of deserializing.
     */
    fun deserialize(
        result: CompileResult.Success,
        cache: MutableMap<Int, KJvmCompiledScript>?,
    ): LinkedSnippet<KJvmCompiledScript> {
        require(result.allScriptHashCodes.isNotEmpty()) { "Hash codes list is empty" }

        val deserializedScriptsByHashCode by lazy {
            val scripts = deserializeObject<List<KJvmCompiledScript>>(result.serializedNewCompiledScripts)
            require(scripts.size == result.newScriptHashCodes.size) {
                "Deserialized scripts count (${scripts.size}) doesn't match newScriptHashCodes count (${result.newScriptHashCodes.size})"
            }
            result.newScriptHashCodes.zip(scripts).toMap()
        }

        // Reconstruct the LinkedSnippet chain using allScriptHashCodes for ordering.
        // Scripts present in the cache are reused; others are taken from the deserialized map.
        var snippet: LinkedSnippet<KJvmCompiledScript>? = null
        for (hashCode in result.allScriptHashCodes) {
            val script =
                cache?.getOrPut(hashCode) { deserializedScriptsByHashCode.getValue(hashCode) }
                    ?: deserializedScriptsByHashCode.getValue(hashCode)
            snippet = linkedSnippet(previous = snippet, script)
        }
        return snippet!! // since result.allScriptHashCodes is not empty
    }

    private fun linkedSnippet(
        previous: LinkedSnippet<KJvmCompiledScript>?,
        script: KJvmCompiledScript,
    ): LinkedSnippet<KJvmCompiledScript> =
        object : LinkedSnippet<KJvmCompiledScript> {
            override val previous: LinkedSnippet<KJvmCompiledScript>? = previous

            override fun get(): KJvmCompiledScript = script
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
