package org.jetbrains.kotlinx.jupyter.compiler.impl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResult
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyAnnotation
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyResolutionResult
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResultDeserializer
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for CompilerServiceImpl, specifically testing compiled snippet serialization.
 *
 * These tests verify that compiled snippets can be serialized for transmission over gRPC.
 * The implementation converts LinkedSnippet to a List before serialization to avoid
 * issues with non-serializable references in the linked structure.
 */

class CompilerServiceImplTest {
    private val callbacks = object : KernelCallbacks {
        override suspend fun reportImports(imports: List<String>) {}
        override suspend fun reportDeclarations(declarations: List<DeclarationInfo>) {}
        override suspend fun resolveDependencies(annotations: List<DependencyAnnotation>): DependencyResolutionResult {
            return DependencyResolutionResult.Success(emptyList())
        }
        override suspend fun updatedClasspath(): List<String> = emptyList()
    }

    // Get Kotlin stdlib from test classpath
    private val kotlinStdlibClasspath = System.getProperty("java.class.path")
        .split(File.pathSeparator)
        .filter { it.contains("kotlin-stdlib") || it.contains("kotlin-reflect") }

    @Test
    fun `should serialize compiled snippet successfully`() = runBlocking<Unit> {
        // Create compiler service with Kotlin stdlib in classpath
        val compiler = createCompilerService()

        // Compile a simple snippet
        val code = "val x = 42"

        // The test verifies that compilation succeeds and produces serialized output
        // The actual serialization is tested by the fact that CompilerServiceImpl.compile
        // calls serializeObject() internally, and any serialization errors would cause
        // the compile() call to fail
        val result = try {
            compiler.compile(
                snippetId = 0,
                code = code,
                cellId = 0,
                isUserCode = true,
            )
        } catch (e: Exception) {
            throw AssertionError("Compilation and serialization should succeed, but got: ${e.message}", e)
        }

        // Verify compilation succeeded
        assertTrue(result is CompileResult.Success, "Expected successful compilation")

        // Verify serialized data is not empty and has reasonable size
        assertTrue(result.serializedCompiledSnippet.isNotEmpty(), "Serialized snippet should not be empty")
        assertTrue(result.serializedCompiledSnippet.size > 100, "Serialized snippet should contain actual data")

        // Test deserialization
        val deserializedSnippet = CompileResultDeserializer.deserialize(result, cache = null)
        assertNotNull(deserializedSnippet, "Deserialized snippet should not be null")

        // Verify the deserialized snippet contains a compiled script
        val compiledScript: KJvmCompiledScript = deserializedSnippet.get()
        assertNotNull(compiledScript, "Compiled script should not be null")
    }

    @Test
    fun `should serialize snippet with multiple statements`() = runBlocking<Unit> {
        val compiler = createCompilerService()

        // Compile a snippet with multiple statements
        val code = """
            val x = 42
            val y = x + 1
            fun foo() = y * 2
            foo()
        """.trimIndent()

        val result = compiler.compile(
            snippetId = 1,
            code = code,
            cellId = 1,
            isUserCode = true,
        )

        assertTrue(result is CompileResult.Success, "Expected successful compilation")

        // Verify serialization
        assertTrue(result.serializedCompiledSnippet.isNotEmpty())
        assertTrue(result.serializedCompiledSnippet.size > 100, "Should contain compiled data")

        // Test deserialization works correctly
        val deserializedSnippet = CompileResultDeserializer.deserialize(result, cache = null)
        assertNotNull(deserializedSnippet)
        assertNotNull(deserializedSnippet.get())
    }

    @Test
    fun `should handle compilation errors without serialization`() = runBlocking {
        val compiler = createCompilerService()

        // Compile invalid code
        val code = "val x = undefinedVariable"
        val result = compiler.compile(
            snippetId = 2,
            code = code,
            cellId = 2,
            isUserCode = true,
        )

        // Verify compilation failed
        assertTrue(result is CompileResult.Failure, "Expected compilation failure")

        // Verify diagnostics are present
        assertTrue(result.diagnostics.isNotEmpty(), "Should have diagnostics")
    }

    @Test
    fun `serialized snippets should be reusable across multiple compilations`() = runBlocking<Unit> {
        val compiler = createCompilerService()

        // Compile first snippet
        val code1 = "val x = 10"
        val result1 = compiler.compile(
            snippetId = 3,
            code = code1,
            cellId = 3,
            isUserCode = true,
        )
        assertTrue(result1 is CompileResult.Success)

        // Compile second snippet that depends on the first
        val code2 = "val y = x + 5"
        val result2 = compiler.compile(
            snippetId = 4,
            code = code2,
            cellId = 4,
            isUserCode = true,
        )
        assertTrue(result2 is CompileResult.Success, "Second compilation should succeed with access to previous snippet")

        // Verify both snippets are serialized
        assertTrue(result1.serializedCompiledSnippet.isNotEmpty())
        assertTrue(result2.serializedCompiledSnippet.isNotEmpty())

        // Test that both can be deserialized
        val snippet1 = CompileResultDeserializer.deserialize(result1, cache = null)
        val snippet2 = CompileResultDeserializer.deserialize(result2, cache = null)
        assertNotNull(snippet1)
        assertNotNull(snippet2)

        // Verify snippet2 has a reference to snippet1 through the previous chain
        assertNotNull(snippet2.previous, "Second snippet should reference the first")
    }

    @Test
    fun `should populate cache when deserializing with cache parameter`() = runBlocking {
        val compiler = createCompilerService()

        // Compile a snippet
        val code = "val x = 42"
        val result = compiler.compile(
            snippetId = 5,
            code = code,
            cellId = 5,
            isUserCode = true,
        )
        assertTrue(result is CompileResult.Success)

        // Create a cache and deserialize with it (first time)
        val cache = mutableMapOf<Int, KJvmCompiledScript>()
        val deserializedSnippet1 = CompileResultDeserializer.deserialize(result, cache)

        // Verify cache was populated
        assertTrue(cache.isNotEmpty(), "Cache should be populated")
        assertEquals(cache.size, result.scriptHashCodes.size, "Cache should contain all scripts")

        // Verify hash codes match
        for (hashcode in result.scriptHashCodes) {
            assertTrue(cache.containsKey(hashcode), "Cache should contain script with hashcode $hashcode")
            assertNotNull(cache[hashcode], "Cached script should not be null")
        }

        // Verify the deserialized snippet is valid
        assertNotNull(deserializedSnippet1)
        val script1 = deserializedSnippet1.get()
        assertNotNull(script1)

        // Deserialize again with the same cache (second time)
        val deserializedSnippet2 = CompileResultDeserializer.deserialize(result, cache)
        val script2 = deserializedSnippet2.get()
        assertNotNull(script2)

        assertSame(script1, script2, "Same scripts from different deserializations should be identical instances")
    }

    private fun createCompilerService(): CompilerService {
        val params = CompilerParams(
            scriptClasspath = kotlinStdlibClasspath,
            jvmTarget = "11",
            scriptReceiverCanonicalNames = emptyList(),
            replCompilerMode = ReplCompilerMode.K2,
        )

        return CompilerServiceImpl(params, callbacks, DefaultKernelLoggerFactory)
    }
}
