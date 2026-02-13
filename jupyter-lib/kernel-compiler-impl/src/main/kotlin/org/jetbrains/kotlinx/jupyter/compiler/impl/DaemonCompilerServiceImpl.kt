package org.jetbrains.kotlinx.jupyter.compiler.impl

import com.google.protobuf.ByteString
import org.jetbrains.kotlinx.jupyter.api.DEFAULT
import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.DeclarationKind
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResult
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyResolutionResult
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.compiler.proto.AddClasspathEntriesRequest
import org.jetbrains.kotlinx.jupyter.compiler.proto.AddClasspathEntriesResponse
import org.jetbrains.kotlinx.jupyter.compiler.proto.AnnotationType
import org.jetbrains.kotlinx.jupyter.compiler.proto.CompileRequest
import org.jetbrains.kotlinx.jupyter.compiler.proto.CompileResponse
import org.jetbrains.kotlinx.jupyter.compiler.proto.DeclarationType
import org.jetbrains.kotlinx.jupyter.compiler.proto.DependencyAnnotation
import org.jetbrains.kotlinx.jupyter.compiler.proto.Diagnostic
import org.jetbrains.kotlinx.jupyter.compiler.proto.DiagnosticSeverity
import org.jetbrains.kotlinx.jupyter.compiler.proto.InitializeRequest
import org.jetbrains.kotlinx.jupyter.compiler.proto.InitializeResponse
import org.jetbrains.kotlinx.jupyter.compiler.proto.JupyterCompilerServiceGrpcKt
import org.jetbrains.kotlinx.jupyter.compiler.proto.KernelCallbackServiceGrpcKt
import org.jetbrains.kotlinx.jupyter.compiler.proto.ReportDeclarationsRequest
import org.jetbrains.kotlinx.jupyter.compiler.proto.ReportImportsRequest
import org.jetbrains.kotlinx.jupyter.compiler.proto.ResolveDependenciesRequest
import org.jetbrains.kotlinx.jupyter.compiler.proto.SourceLocation
import org.jetbrains.kotlinx.jupyter.compiler.proto.SourcePosition
import kotlin.script.experimental.api.ScriptDiagnostic
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyAnnotation as ApiDependencyAnnotation

/**
 * gRPC service implementation for the compiler daemon.
 * Implements the JupyterCompilerService defined in the proto file.
 * This runs inside the daemon process.
 */
class DaemonCompilerServiceImpl(
    private val callbackStub: KernelCallbackServiceGrpcKt.KernelCallbackServiceCoroutineStub,
) : JupyterCompilerServiceGrpcKt.JupyterCompilerServiceCoroutineImplBase() {
    private var compiler: CompilerService? = null

    override suspend fun initialize(request: InitializeRequest): InitializeResponse {
        val params =
            CompilerParams(
                scriptClasspath = request.classpathEntriesList,
                jvmTarget = request.jvmTarget,
                scriptReceiverCanonicalNames = request.scriptReceiverCanonicalNamesList,
                replCompilerMode = request.replCompilerMode.toApi(),
            )

        val callbacks = GrpcKernelCallbacks(callbackStub)
        compiler = CompilerServiceImpl(params, callbacks)

        return InitializeResponse
            .newBuilder()
            .setSuccess(true)
            .build()
    }

    override suspend fun compile(request: CompileRequest): CompileResponse {
        val currentCompiler =
            compiler ?: return CompileResponse
                .newBuilder()
                .setSuccess(false)
                .addDiagnostics(
                    Diagnostic
                        .newBuilder()
                        .setSeverity(DiagnosticSeverity.ERROR)
                        .setMessage("Compiler not initialized")
                        .build(),
                ).build()

        return when (
            val result =
                currentCompiler.compile(
                    snippetId = request.snippetId,
                    code = request.code,
                    cellId = request.cellId,
                )
        ) {
            is CompileResult.Success ->
                CompileResponse
                    .newBuilder()
                    .setSuccess(true)
                    .setSerializedCompiledSnippet(ByteString.copyFrom(result.serializedCompiledSnippet))
                    .build()

            is CompileResult.Failure ->
                CompileResponse
                    .newBuilder()
                    .setSuccess(false)
                    .addAllDiagnostics(result.diagnostics.map { it.toProto() })
                    .build()
        }
    }

    override suspend fun addClasspathEntries(request: AddClasspathEntriesRequest): AddClasspathEntriesResponse {
        val currentCompiler = compiler
        if (currentCompiler != null) {
            currentCompiler.addClasspathEntries(request.classpathEntriesList)
        }
        return AddClasspathEntriesResponse
            .newBuilder()
            .setSuccess(currentCompiler != null)
            .build()
    }

}

/**
 * Implements KernelCallbacks by making gRPC calls back to the kernel.
 */
private class GrpcKernelCallbacks(
    private val stub: KernelCallbackServiceGrpcKt.KernelCallbackServiceCoroutineStub,
) : KernelCallbacks {
    override suspend fun reportImports(imports: List<String>) {
        val request =
            ReportImportsRequest
                .newBuilder()
                .addAllImports(imports)
                .build()
        stub.reportImports(request)
    }

    override suspend fun reportDeclarations(declarations: List<DeclarationInfo>) {
        val request =
            ReportDeclarationsRequest
                .newBuilder()
                .addAllDeclarations(declarations.map { it.toProto() })
                .build()
        stub.reportDeclarations(request)
    }

    override suspend fun resolveDependencies(annotations: List<ApiDependencyAnnotation>): DependencyResolutionResult {
        val request =
            ResolveDependenciesRequest
                .newBuilder()
                .addAllAnnotations(annotations.map { it.toProto() })
                .build()
        val response = stub.resolveDependencies(request)

        return if (response.success) {
            DependencyResolutionResult.Success(response.classpathEntriesList)
        } else {
            DependencyResolutionResult.Failure(response.errorMessage)
        }
    }
}

// Extension functions to convert between API and proto types

private fun ScriptDiagnostic.toProto(): Diagnostic =
    Diagnostic
        .newBuilder()
        .setCode(code)
        .setMessage(message)
        .setSeverity(
            when (severity) {
                ScriptDiagnostic.Severity.FATAL -> DiagnosticSeverity.FATAL
                ScriptDiagnostic.Severity.ERROR -> DiagnosticSeverity.ERROR
                ScriptDiagnostic.Severity.WARNING -> DiagnosticSeverity.WARNING
                ScriptDiagnostic.Severity.INFO -> DiagnosticSeverity.INFO
                ScriptDiagnostic.Severity.DEBUG -> DiagnosticSeverity.DEBUG
            },
        )
        .apply {
            this@toProto.sourcePath?.let { setSourcePath(it) }
            this@toProto.location?.let { loc ->
                setLocation(
                    SourceLocation
                        .newBuilder()
                        .setStart(
                            SourcePosition
                                .newBuilder()
                                .setLine(loc.start.line)
                                .setCol(loc.start.col)
                                .build(),
                        )
                        .apply {
                            loc.end?.let { end ->
                                setEnd(
                                    SourcePosition
                                        .newBuilder()
                                        .setLine(end.line)
                                        .setCol(end.col)
                                        .build(),
                                )
                            }
                        }
                        .build(),
                )
            }
        }.build()

private fun DeclarationInfo.toProto(): org.jetbrains.kotlinx.jupyter.compiler.proto.DeclarationInfo =
    org.jetbrains.kotlinx.jupyter.compiler.proto.DeclarationInfo
        .newBuilder()
        .setName(name ?: "")
        .setType(
            when (kind) {
                DeclarationKind.FUNCTION -> DeclarationType.FUNCTION
                DeclarationKind.CLASS -> DeclarationType.CLASS
                DeclarationKind.OBJECT -> DeclarationType.OBJECT
                DeclarationKind.PROPERTY -> DeclarationType.PROPERTY
                else -> DeclarationType.UNKNOWN
            },
        ).build()

private fun ApiDependencyAnnotation.toProto(): DependencyAnnotation =
    DependencyAnnotation
        .newBuilder()
        .setType(
            when (this) {
                is ApiDependencyAnnotation.DependsOn -> AnnotationType.DEPENDS_ON
                is ApiDependencyAnnotation.Repository -> AnnotationType.REPOSITORY
            },
        ).setValue(
            when (this) {
                is ApiDependencyAnnotation.DependsOn -> value
                is ApiDependencyAnnotation.Repository -> url
            },
        ).build()

private fun org.jetbrains.kotlinx.jupyter.compiler.proto.ReplCompilerMode.toApi(): ReplCompilerMode =
    when (this) {
        org.jetbrains.kotlinx.jupyter.compiler.proto.ReplCompilerMode.K1 -> ReplCompilerMode.K1
        org.jetbrains.kotlinx.jupyter.compiler.proto.ReplCompilerMode.K2 -> ReplCompilerMode.K2
        org.jetbrains.kotlinx.jupyter.compiler.proto.ReplCompilerMode.UNRECOGNIZED -> ReplCompilerMode.DEFAULT
    }
