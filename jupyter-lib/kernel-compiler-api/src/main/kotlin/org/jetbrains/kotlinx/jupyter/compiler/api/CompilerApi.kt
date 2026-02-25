package org.jetbrains.kotlinx.jupyter.compiler.api

import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCodeCompletionVariant

/**
 * Result of compilation.
 * Contains serialized LinkedSnippet<KJvmCompiledScript> which can be deserialized on the kernel side.
 */
sealed class CompileResult {
    /**
     * Successful compilation.
     * @param serializedCompiledSnippet Java-serialized LinkedSnippet<KJvmCompiledScript>
     * @param scriptHashCodes Hash codes for each KJvmCompiledScript in the list (in same order)
     */
    data class Success(
        val serializedCompiledSnippet: ByteArray,
        val scriptHashCodes: List<Int>,
    ) : CompileResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Success
            return serializedCompiledSnippet.contentEquals(other.serializedCompiledSnippet) &&
                scriptHashCodes == other.scriptHashCodes
        }

        override fun hashCode(): Int {
            var result = serializedCompiledSnippet.contentHashCode()
            result = 31 * result + scriptHashCodes.hashCode()
            return result
        }
    }

    data class Failure(
        val diagnostics: List<ScriptDiagnostic>,
    ) : CompileResult()
}

/**
 * Dependency annotation from notebook code.
 */
sealed class DependencyAnnotation {
    data class DependsOn(
        val value: String,
    ) : DependencyAnnotation()

    data class Repository(
        val url: String,
        val username: String = "",
        val password: String = "",
    ) : DependencyAnnotation()
}

/**
 * Result of dependency resolution.
 */
sealed class DependencyResolutionResult {
    data class Success(
        val classpathEntries: List<String>,
    ) : DependencyResolutionResult()

    data class Failure(
        val message: String,
    ) : DependencyResolutionResult()
}

/**
 * The main RPC-based compiler service API.
 * This interface is used for communication between kernel and compiler (daemon or in-process).
 * Note: This is distinct from the kernel's internal JupyterCompiler which is used daemon-side.
 */
interface CompilerService {
    suspend fun compile(
        snippetId: Int,
        code: String,
        cellId: Int,
        isUserCode: Boolean,
    ): CompileResult

    suspend fun complete(
        code: String,
        id: Int,
        position: SourceCode.Position,
    ): List<SourceCodeCompletionVariant>

    suspend fun listErrors(
        code: String,
        id: Int,
    ): List<ScriptDiagnostic>

    suspend fun checkComplete(code: String): Boolean

    suspend fun getClasspath(): List<String>
}

/**
 * Callback interface that the kernel implements.
 * The compiler calls back to the kernel for certain operations.
 */
interface KernelCallbacks {
    suspend fun reportImports(imports: List<String>)

    suspend fun reportDeclarations(declarations: List<DeclarationInfo>)

    suspend fun resolveDependencies(annotations: List<DependencyAnnotation>): DependencyResolutionResult

    suspend fun updatedClasspath(): List<String>
}

/**
 * SPI interface for loading compiler service implementations.
 * Supports both in-process (test) and out-of-process (daemon) compilation.
 */
interface CompilerServiceProvider {
    /**
     * Priority for SPI loading. Higher priority providers are preferred.
     * Test implementations should return a higher priority (e.g., 100).
     * Production daemon should return a lower priority (e.g., 10).
     */
    val priority: Int

    /**
     * Create a compiler service instance.
     */
    fun createCompiler(
        params: CompilerParams,
        callbacks: KernelCallbacks,
    ): CompilerService
}

/**
 * Parameters for initializing the compiler.
 */
data class CompilerParams(
    val scriptClasspath: List<String>,
    val jvmTarget: String,
    val scriptReceiverCanonicalNames: List<String> = emptyList(),
    val replCompilerMode: ReplCompilerMode = ReplCompilerMode.K1,
)
