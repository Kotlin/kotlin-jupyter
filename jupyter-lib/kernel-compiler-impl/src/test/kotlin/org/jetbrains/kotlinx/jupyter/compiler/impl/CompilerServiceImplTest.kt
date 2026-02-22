package org.jetbrains.kotlinx.jupyter.compiler.impl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResult
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyAnnotation
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyResolutionResult
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.util.LinkedSnippet
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for CompilerServiceImpl, specifically testing compiled snippet serialization.
 *
 * NOTE: These tests currently fail with NotSerializableException because LinkedSnippet
 * and its contents are not serializable using Java serialization. This is a known issue
 * that needs to be addressed for the daemon compiler to work properly over gRPC.
 *
 * Possible solutions:
 * 1. Implement custom serialization for LinkedSnippet
 * 2. Extract only serializable data from LinkedSnippet before serialization
 * 3. Use a different serialization mechanism (Protobuf, custom binary format)
 */

class CompilerServiceImplTest {
    private val callbacks = object : KernelCallbacks {
        override suspend fun reportImports(imports: List<String>) {}
        override suspend fun reportDeclarations(declarations: List<DeclarationInfo>) {}
        override suspend fun resolveDependencies(annotations: List<DependencyAnnotation>): DependencyResolutionResult {
            return DependencyResolutionResult.Success(emptyList())
        }
    }

    // Get Kotlin stdlib from test classpath
    private val kotlinStdlibClasspath = System.getProperty("java.class.path")
        .split(System.getProperty("path.separator"))
        .filter { it.contains("kotlin-stdlib") || it.contains("kotlin-reflect") }

    @Test
    fun `should serialize compiled snippet successfully`() = runBlocking {
        // Create compiler service with Kotlin stdlib in classpath
        val params = CompilerParams(
            scriptClasspath = kotlinStdlibClasspath,
            jvmTarget = "11",
            scriptReceiverCanonicalNames = emptyList(),
            replCompilerMode = ReplCompilerMode.K2,
        )

        val compiler = CompilerServiceImpl(params, callbacks)

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
            )
        } catch (e: Exception) {
            throw AssertionError("Compilation and serialization should succeed, but got: ${e.message}", e)
        }

        // Verify compilation succeeded
        assertTrue(result is CompileResult.Success, "Expected successful compilation")
        result as CompileResult.Success

        // Verify serialized data is not empty and has reasonable size
        assertTrue(result.serializedCompiledSnippet.isNotEmpty(), "Serialized snippet should not be empty")
        assertTrue(result.serializedCompiledSnippet.size > 100, "Serialized snippet should contain actual data")
    }

    @Test
    fun `should serialize snippet with multiple statements`() = runBlocking {
        val params = CompilerParams(
            scriptClasspath = kotlinStdlibClasspath,
            jvmTarget = "11",
            scriptReceiverCanonicalNames = emptyList(),
            replCompilerMode = ReplCompilerMode.K2,
        )

        val compiler = CompilerServiceImpl(params, callbacks)

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
        )

        assertTrue(result is CompileResult.Success, "Expected successful compilation")
        result as CompileResult.Success

        // Verify serialization
        assertTrue(result.serializedCompiledSnippet.isNotEmpty())
        assertTrue(result.serializedCompiledSnippet.size > 100, "Should contain compiled data")
    }

    @Test
    fun `should handle compilation errors without serialization`() = runBlocking {
        val params = CompilerParams(
            scriptClasspath = kotlinStdlibClasspath,
            jvmTarget = "11",
            scriptReceiverCanonicalNames = emptyList(),
            replCompilerMode = ReplCompilerMode.K2,
        )

        val compiler = CompilerServiceImpl(params, callbacks)

        // Compile invalid code
        val code = "val x = undefinedVariable"
        val result = compiler.compile(
            snippetId = 2,
            code = code,
            cellId = 2,
        )

        // Verify compilation failed
        assertTrue(result is CompileResult.Failure, "Expected compilation failure")
        result as CompileResult.Failure

        // Verify diagnostics are present
        assertTrue(result.diagnostics.isNotEmpty(), "Should have diagnostics")
    }

    @Test
    fun `serialized snippets should be reusable across multiple compilations`() = runBlocking {
        val params = CompilerParams(
            scriptClasspath = kotlinStdlibClasspath,
            jvmTarget = "11",
            scriptReceiverCanonicalNames = emptyList(),
            replCompilerMode = ReplCompilerMode.K2,
        )

        val compiler = CompilerServiceImpl(params, callbacks)

        // Compile first snippet
        val code1 = "val x = 10"
        val result1 = compiler.compile(
            snippetId = 3,
            code = code1,
            cellId = 3,
        )
        assertTrue(result1 is CompileResult.Success)

        // Compile second snippet that depends on the first
        val code2 = "val y = x + 5"
        val result2 = compiler.compile(
            snippetId = 4,
            code = code2,
            cellId = 4,
        )
        assertTrue(result2 is CompileResult.Success, "Second compilation should succeed with access to previous snippet")
        result2 as CompileResult.Success

        // Verify both snippets are serialized
        val success1 = result1 as CompileResult.Success
        assertTrue(success1.serializedCompiledSnippet.isNotEmpty())
        assertTrue(result2.serializedCompiledSnippet.isNotEmpty())
    }
}
