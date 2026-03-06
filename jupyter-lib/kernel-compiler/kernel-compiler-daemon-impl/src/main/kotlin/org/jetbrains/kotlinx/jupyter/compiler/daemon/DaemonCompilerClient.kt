package org.jetbrains.kotlinx.jupyter.compiler.daemon

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.DeclarationKind
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResult
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyResolutionResult
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.compiler.proto.AnnotationType
import org.jetbrains.kotlinx.jupyter.compiler.proto.CompileRequest
import org.jetbrains.kotlinx.jupyter.compiler.proto.DeclarationType
import org.jetbrains.kotlinx.jupyter.compiler.proto.Diagnostic
import org.jetbrains.kotlinx.jupyter.compiler.proto.DiagnosticSeverity
import org.jetbrains.kotlinx.jupyter.compiler.proto.InitializeRequest.newBuilder
import org.jetbrains.kotlinx.jupyter.compiler.proto.JupyterCompilerServiceGrpcKt
import org.jetbrains.kotlinx.jupyter.compiler.proto.KernelCallbackServiceGrpcKt
import org.jetbrains.kotlinx.jupyter.compiler.proto.ReportDeclarationsRequest
import org.jetbrains.kotlinx.jupyter.compiler.proto.ReportDeclarationsResponse
import org.jetbrains.kotlinx.jupyter.compiler.proto.ReportImportsRequest
import org.jetbrains.kotlinx.jupyter.compiler.proto.ReportImportsResponse
import org.jetbrains.kotlinx.jupyter.compiler.proto.ResolveDependenciesRequest
import org.jetbrains.kotlinx.jupyter.compiler.proto.ResolveDependenciesResponse
import org.jetbrains.kotlinx.jupyter.compiler.proto.UpdatedClasspathRequest
import org.jetbrains.kotlinx.jupyter.compiler.proto.UpdatedClasspathResponse
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import java.io.Closeable
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCodeCompletionVariant
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyAnnotation as ApiDependencyAnnotation

/**
 * Client that communicates with the compiler daemon via gRPC.
 */
class DaemonCompilerClient(
    params: CompilerParams,
    callbacks: KernelCallbacks,
    loggerFactory: KernelLoggerFactory,
) : CompilerService, Closeable {
    private val processHandler = DaemonProcessHandler({
        KernelCallbackServiceImpl(callbacks = callbacks, reportDaemonPort = it::reportDaemonPort)
    }, loggerFactory)

    private val logger = loggerFactory.getLogger(DaemonCompilerClient::class.java)

    private val stub: JupyterCompilerServiceGrpcKt.JupyterCompilerServiceCoroutineStub =
        JupyterCompilerServiceGrpcKt.JupyterCompilerServiceCoroutineStub(processHandler.channel)

    init {
        // Initialize the compiler on the daemon
        runBlocking {
            val request =
                newBuilder()
                    .addAllClasspathEntries(params.scriptClasspath)
                    .setJvmTarget(params.jvmTarget)
                    .addAllScriptReceiverCanonicalNames(params.scriptReceiverCanonicalNames)
                    .setReplCompilerMode(params.replCompilerMode.toProto())
                    .addAllExtraCompilerArguments(params.extraCompilerArguments)
                    .build()

            val response = stub.initialize(request)
            if (!response.success) {
                val errorMsg = if (response.hasErrorMessage()) response.errorMessage else "Unknown error"
                throw RuntimeException("Failed to initialize compiler daemon: $errorMsg")
            }
        }
        logger.debug("Compiler daemon initialized successfully")
    }

    override fun close() {
        processHandler.close()
    }

    override suspend fun compile(
        snippetId: Int,
        code: String,
        cellId: Int,
        isUserCode: Boolean,
    ): CompileResult {
        val request =
            CompileRequest
                .newBuilder()
                .setSnippetId(snippetId)
                .setCode(code)
                .setCellId(cellId)
                .setIsUserCode(isUserCode)
                .build()

        val response = stub.compile(request)

        return if (response.success) {
            CompileResult.Success(
                serializedCompiledSnippet = response.serializedCompiledSnippet.toByteArray(),
                scriptHashCodes = response.scriptHashCodesList,
            )
        } else {
            CompileResult.Failure(response.diagnosticsList.map { it.fromProto() })
        }
    }

    override suspend fun complete(
        code: String,
        id: Int,
        position: SourceCode.Position,
    ): List<SourceCodeCompletionVariant> {
        val request =
            org.jetbrains.kotlinx.jupyter.compiler.proto.CompleteRequest
                .newBuilder()
                .setCode(code)
                .setId(id)
                .setPosition(position.toProto())
                .build()

        val response = stub.complete(request)

        if (!response.success) {
            val errorMsg = if (response.hasErrorMessage()) response.errorMessage else "Unknown error"
            throw RuntimeException("Code completion failed: $errorMsg")
        }

        return response.completionsList.map { it.fromProto() }
    }

    override suspend fun listErrors(
        code: String,
        id: Int,
    ): List<ScriptDiagnostic> {
        val request =
            org.jetbrains.kotlinx.jupyter.compiler.proto.ListErrorsRequest
                .newBuilder()
                .setCode(code)
                .setId(id)
                .build()

        val response = stub.listErrors(request)

        if (!response.success) {
            val errorMsg = if (response.hasErrorMessage()) response.errorMessage else "Unknown error"
            throw RuntimeException("Failed to list errors: $errorMsg")
        }

        return response.diagnosticsList.map { it.fromProto() }
    }

    override suspend fun checkComplete(
        code: String,
        snippetId: Int,
    ): Boolean {
        val request =
            org.jetbrains.kotlinx.jupyter.compiler.proto.CheckCompleteRequest
                .newBuilder()
                .setCode(code)
                .setSnippetId(snippetId)
                .build()

        val response = stub.checkComplete(request)

        if (!response.success) {
            val errorMsg = if (response.hasErrorMessage()) response.errorMessage else "Unknown error"
            throw RuntimeException("Failed to check code completeness: $errorMsg")
        }

        return response.isComplete
    }

    override suspend fun getClasspath(): List<String> {
        val request =
            org.jetbrains.kotlinx.jupyter.compiler.proto.GetClasspathRequest
                .newBuilder()
                .build()

        val response = stub.getClasspath(request)

        if (!response.success) {
            val errorMsg = if (response.hasErrorMessage()) response.errorMessage else "Unknown error"
            throw RuntimeException("Failed to get classpath: $errorMsg")
        }

        return response.classpathEntriesList
    }
}

/**
 * Implements the callback service that the daemon will call.
 */
private class KernelCallbackServiceImpl(
    private val callbacks: KernelCallbacks,
    private val reportDaemonPort: (Int) -> Unit,
) : KernelCallbackServiceGrpcKt.KernelCallbackServiceCoroutineImplBase() {
    override suspend fun reportDaemonPort(
        request: org.jetbrains.kotlinx.jupyter.compiler.proto.ReportDaemonPortRequest,
    ): org.jetbrains.kotlinx.jupyter.compiler.proto.ReportDaemonPortResponse {
        reportDaemonPort(request.port)
        return org.jetbrains.kotlinx.jupyter.compiler.proto.ReportDaemonPortResponse
            .newBuilder()
            .build()
    }

    override suspend fun reportImports(request: ReportImportsRequest): ReportImportsResponse {
        callbacks.reportImports(request.importsList)
        return ReportImportsResponse.newBuilder().build()
    }

    override suspend fun reportDeclarations(request: ReportDeclarationsRequest): ReportDeclarationsResponse {
        val declarations = request.declarationsList.map { it.fromProto() }
        callbacks.reportDeclarations(declarations)
        return ReportDeclarationsResponse.newBuilder().build()
    }

    override suspend fun resolveDependencies(request: ResolveDependenciesRequest): ResolveDependenciesResponse {
        val annotations = request.annotationsList.map { it.fromProtoToApi() }
        return when (val result = callbacks.resolveDependencies(annotations)) {
            is DependencyResolutionResult.Success ->
                ResolveDependenciesResponse
                    .newBuilder()
                    .setSuccess(true)
                    .addAllClasspathEntries(result.classpathEntries)
                    .build()
            is DependencyResolutionResult.Failure ->
                ResolveDependenciesResponse
                    .newBuilder()
                    .setSuccess(false)
                    .setErrorMessage(result.message)
                    .build()
        }
    }

    override suspend fun updatedClasspath(request: UpdatedClasspathRequest): UpdatedClasspathResponse =
        try {
            val classpath = callbacks.updatedClasspath()
            UpdatedClasspathResponse
                .newBuilder()
                .setSuccess(true)
                .addAllClasspathEntries(classpath)
                .build()
        } catch (e: Exception) {
            UpdatedClasspathResponse
                .newBuilder()
                .setSuccess(false)
                .setErrorMessage(e.message ?: "Unknown error")
                .build()
        }
}

// Extension functions to convert from proto to API types

private fun Diagnostic.fromProto(): ScriptDiagnostic =
    ScriptDiagnostic(
        code = code,
        message = message,
        severity =
            when (severity) {
                DiagnosticSeverity.FATAL -> ScriptDiagnostic.Severity.FATAL
                DiagnosticSeverity.ERROR -> ScriptDiagnostic.Severity.ERROR
                DiagnosticSeverity.WARNING -> ScriptDiagnostic.Severity.WARNING
                DiagnosticSeverity.INFO -> ScriptDiagnostic.Severity.INFO
                DiagnosticSeverity.DEBUG -> ScriptDiagnostic.Severity.DEBUG
                DiagnosticSeverity.UNRECOGNIZED -> error("Unrecognized DiagnosticSeverity: $severity")
            },
        sourcePath = if (hasSourcePath()) sourcePath else null,
        location =
            if (hasLocation()) {
                SourceCode.Location(
                    start = SourceCode.Position(location.start.line, location.start.col),
                    end =
                        if (location.hasEnd()) {
                            SourceCode.Position(location.end.line, location.end.col)
                        } else {
                            null
                        },
                )
            } else {
                null
            },
    )

private fun org.jetbrains.kotlinx.jupyter.compiler.proto.DeclarationInfo.fromProto(): DeclarationInfo =
    SimpleDeclarationInfo(
        name = name,
        kind =
            when (type) {
                DeclarationType.FUNCTION -> DeclarationKind.FUNCTION
                DeclarationType.CLASS -> DeclarationKind.CLASS
                DeclarationType.OBJECT -> DeclarationKind.OBJECT
                DeclarationType.PROPERTY -> DeclarationKind.PROPERTY
                DeclarationType.SCRIPT_INITIALIZER -> DeclarationKind.SCRIPT_INITIALIZER
                DeclarationType.UNKNOWN -> DeclarationKind.UNKNOWN
                DeclarationType.UNRECOGNIZED -> error("Unrecognized DeclarationType: $type")
            },
    )

private fun org.jetbrains.kotlinx.jupyter.compiler.proto.DependencyAnnotation.fromProtoToApi(): ApiDependencyAnnotation =
    when (type) {
        AnnotationType.DEPENDS_ON -> ApiDependencyAnnotation.DependsOn(value)
        AnnotationType.REPOSITORY -> ApiDependencyAnnotation.Repository(value)
        AnnotationType.UNRECOGNIZED -> error("Unrecognized AnnotationType: $type")
    }

private fun ReplCompilerMode.toProto(): org.jetbrains.kotlinx.jupyter.compiler.proto.ReplCompilerMode =
    when (this) {
        ReplCompilerMode.K1 -> org.jetbrains.kotlinx.jupyter.compiler.proto.ReplCompilerMode.K1
        ReplCompilerMode.K2 -> org.jetbrains.kotlinx.jupyter.compiler.proto.ReplCompilerMode.K2
    }

private fun SourceCode.Position.toProto(): org.jetbrains.kotlinx.jupyter.compiler.proto.SourceCodePosition =
    org.jetbrains.kotlinx.jupyter.compiler.proto.SourceCodePosition
        .newBuilder()
        .setLine(line)
        .setCol(col)
        .setAbsolutePos(absolutePos ?: 0)
        .build()

private fun org.jetbrains.kotlinx.jupyter.compiler.proto.SourceCodeCompletionVariant.fromProto(): SourceCodeCompletionVariant =
    SourceCodeCompletionVariant(
        text = text,
        displayText = displayText,
        tail = tail,
        icon = icon,
        deprecationLevel =
            if (hasDeprecationLevel()) {
                DeprecationLevel.valueOf(deprecationLevel)
            } else {
                null
            },
    )

/**
 * Simple implementation of DeclarationInfo for proto conversion.
 */
private data class SimpleDeclarationInfo(
    override val name: String?,
    override val kind: DeclarationKind,
) : DeclarationInfo
