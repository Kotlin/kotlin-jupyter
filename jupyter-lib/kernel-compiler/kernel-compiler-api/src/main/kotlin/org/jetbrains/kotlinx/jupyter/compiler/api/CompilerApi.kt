package org.jetbrains.kotlinx.jupyter.compiler.api

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCodeCompletionVariant

/**
 * RPC-compatible compiler service API.
 * This interface is used for communication between kernel and compiler (RPC-based daemon or in-process).
 */
interface CompilerService {
    suspend fun getCompilerData(): CompilerData

    suspend fun compile(
        snippetId: Int,
        code: String,
        cellId: Int,
        isUserCode: Boolean,
        cachedScriptHashCodes: List<Int>,
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

    suspend fun checkComplete(
        code: String,
        snippetId: Int,
    ): Boolean

    suspend fun getClasspath(): List<String>
}

@Serializable
data class CompilerParams(
    val scriptClasspath: List<String>,
    val jvmTarget: String,
    val scriptReceiverCanonicalNames: List<String> = emptyList(),
    val replCompilerMode: ReplCompilerMode = ReplCompilerMode.K1,
    val extraCompilerArguments: List<String> = emptyList(),
)

@Serializable
data class CompilerData(
    val version: String,
    val fileExtension: String,
)

sealed class CompileResult {
    /**
     * Successful compilation result.
     *
     * @property serializedNewCompiledScripts Java-serialized `List<KJvmCompiledScript>` with uncached scripts only
     * @property newScriptHashCodes Hash codes corresponding to [serializedNewCompiledScripts]
     * @property allScriptHashCodes Complete chain of hash codes (cached + new), ordered oldest-first
     */
    @Serializable
    class Success(
        val serializedNewCompiledScripts: ByteArray,
        val newScriptHashCodes: List<Int>,
        val allScriptHashCodes: List<Int>,
        val imports: List<String>,
        val declarations: List<DeclarationInfo>,
    ) : CompileResult()

    data class Failure(
        val diagnostics: List<ScriptDiagnostic>,
    ) : CompileResult()
}

/**
 * Callback interface that the kernel implements.
 * The compiler calls back to the kernel for certain operations.
 */
interface KernelCallbacks {
    suspend fun resolveDependencies(annotations: List<DependencyAnnotation>): DependencyResolutionResult

    suspend fun updatedClasspath(): List<String>
}

/** Dependency annotation from notebook code: @DependsOn or @Repository */
@Serializable
sealed class DependencyAnnotation {
    @Serializable
    data class DependsOn(
        val value: String,
    ) : DependencyAnnotation()

    @Serializable
    data class Repository(
        val url: String,
        val username: String,
        val password: String,
    ) : DependencyAnnotation()
}

@Serializable
sealed class DependencyResolutionResult {
    @Serializable
    data class Success(
        val classpathEntries: List<String>,
    ) : DependencyResolutionResult()

    @Serializable
    data class Failure(
        val message: String,
    ) : DependencyResolutionResult()
}
