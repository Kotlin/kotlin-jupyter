package org.jetbrains.kotlinx.jupyter.compiler.api

import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.DeclarationKind

/**
 * Result of compilation.
 * Contains serialized LinkedSnippet<KJvmCompiledScript> and ScriptEvaluationConfiguration
 * which can be deserialized on the kernel side.
 */
sealed class CompileResult {
    /**
     * Successful compilation.
     * @param serializedCompiledSnippet Java-serialized LinkedSnippet<KJvmCompiledScript>
     * @param serializedEvalConfig Java-serialized ScriptEvaluationConfiguration
     */
    data class Success(
        val serializedCompiledSnippet: ByteArray,
        val serializedEvalConfig: ByteArray,
    ) : CompileResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Success
            return serializedCompiledSnippet.contentEquals(other.serializedCompiledSnippet) &&
                serializedEvalConfig.contentEquals(other.serializedEvalConfig)
        }

        override fun hashCode(): Int {
            return 31 * serializedCompiledSnippet.contentHashCode() + serializedEvalConfig.contentHashCode()
        }
    }

    data class Failure(
        val diagnostics: List<Diagnostic>,
    ) : CompileResult()
}

/**
 * Diagnostic message from compilation.
 */
data class Diagnostic(
    val severity: Severity,
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
) {
    enum class Severity { ERROR, WARNING, INFO }
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
    ): CompileResult
}

/**
 * Callback interface that the kernel implements.
 * The compiler calls back to the kernel for certain operations.
 */
interface KernelCallbacks {
    suspend fun reportImports(imports: List<String>)

    suspend fun reportDeclarations(declarations: List<DeclarationInfo>)

    suspend fun resolveDependencies(annotations: List<DependencyAnnotation>): DependencyResolutionResult
}

/**
 * SPI interface for loading compiler service implementations.
 * Supports both in-process (test) and out-of-process (daemon) compilation.
 */
interface CompilerServiceProvider {
    /**
     * Priority for SPI loading. Higher priority providers are preferred.
     * Test implementations should return higher priority (e.g., 100).
     * Production daemon should return lower priority (e.g., 10).
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
)
