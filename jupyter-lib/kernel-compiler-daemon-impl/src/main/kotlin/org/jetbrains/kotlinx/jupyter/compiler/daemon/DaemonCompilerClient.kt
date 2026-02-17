package org.jetbrains.kotlinx.jupyter.compiler.daemon

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
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
import org.jetbrains.kotlinx.jupyter.compiler.proto.InitializeRequest
import org.jetbrains.kotlinx.jupyter.compiler.proto.JupyterCompilerServiceGrpcKt
import org.jetbrains.kotlinx.jupyter.compiler.proto.KernelCallbackServiceGrpcKt
import org.jetbrains.kotlinx.jupyter.compiler.proto.ReportDeclarationsRequest
import org.jetbrains.kotlinx.jupyter.compiler.proto.ReportDeclarationsResponse
import org.jetbrains.kotlinx.jupyter.compiler.proto.ReportImportsRequest
import org.jetbrains.kotlinx.jupyter.compiler.proto.ReportImportsResponse
import org.jetbrains.kotlinx.jupyter.compiler.proto.ResolveDependenciesRequest
import org.jetbrains.kotlinx.jupyter.compiler.proto.ResolveDependenciesResponse
import org.jetbrains.kotlinx.jupyter.protocol.startup.PortsGenerator
import org.jetbrains.kotlinx.jupyter.protocol.startup.create
import java.io.Closeable
import java.io.File
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCodeCompletionVariant
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyAnnotation as ApiDependencyAnnotation

/**
 * Client that communicates with the compiler daemon via gRPC.
 */
class DaemonCompilerClient(
    private val params: CompilerParams,
    private val callbacks: KernelCallbacks,
) : CompilerService, Closeable {
    private var channel: ManagedChannel? = null
    private var stub: JupyterCompilerServiceGrpcKt.JupyterCompilerServiceCoroutineStub? = null
    private var callbackServer: Server? = null
    private var daemonProcess: Process? = null

    private val portsGenerator = PortsGenerator.create(32768, 65536)

    private val daemonPort: Int = portsGenerator.randomPort()
    private val callbackPort: Int = portsGenerator.randomPort()

    init {
        startDaemon()
        // Register shutdown hook to ensure daemon is killed when JVM exits
        Runtime.getRuntime().addShutdownHook(Thread {
            close()
        })
    }

    private fun startDaemon() {
        // Start callback server for daemon to call back to kernel
        val callbackService = KernelCallbackServiceImpl(callbacks)
        callbackServer =
            ServerBuilder
                .forPort(callbackPort)
                .addService(callbackService)
                .build()
                .start()

        println("Kernel callback server started on port $callbackPort")

        // Find the daemon JAR
        val daemonJar = findDaemonJar()

        // Start the daemon process
        val processBuilder =
            ProcessBuilder(
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:1044",
                "-jar",
                daemonJar.absolutePath,
                daemonPort.toString(),
                callbackPort.toString(),
            )

        processBuilder.redirectErrorStream(true)
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)

        daemonProcess = processBuilder.start()

        // Wait a bit for daemon to start
        Thread.sleep(2000)

        // Connect to daemon
        channel =
            ManagedChannelBuilder
                .forAddress("localhost", daemonPort)
                .usePlaintext()
                .build()

        stub = JupyterCompilerServiceGrpcKt.JupyterCompilerServiceCoroutineStub(channel!!)

        // Initialize the compiler on the daemon
        runBlocking {
            val request =
                InitializeRequest
                    .newBuilder()
                    .addAllClasspathEntries(params.scriptClasspath)
                    .setJvmTarget(params.jvmTarget)
                    .addAllScriptReceiverCanonicalNames(params.scriptReceiverCanonicalNames)
                    .setReplCompilerMode(params.replCompilerMode.toProto())
                    .build()

            val response = stub!!.initialize(request)
            if (!response.success) {
                throw RuntimeException("Failed to initialize compiler daemon")
            }
        }

        println("Compiler daemon initialized successfully")
    }

    override suspend fun compile(
        snippetId: Int,
        code: String,
        cellId: Int,
    ): CompileResult {
        val request =
            CompileRequest
                .newBuilder()
                .setSnippetId(snippetId)
                .setCode(code)
                .setCellId(cellId)
                .build()

        val response = stub!!.compile(request)

        return if (response.success) {
            CompileResult.Success(
                serializedCompiledSnippet = response.serializedCompiledSnippet.toByteArray(),
            )
        } else {
            CompileResult.Failure(response.diagnosticsList.map { it.fromProto() })
        }
    }

    override suspend fun addClasspathEntries(classpathEntries: List<String>) {
        val request = org.jetbrains.kotlinx.jupyter.compiler.proto.AddClasspathEntriesRequest
            .newBuilder()
            .addAllClasspathEntries(classpathEntries)
            .build()

        val response = stub!!.addClasspathEntries(request)
        if (!response.success) {
            throw RuntimeException("Failed to add classpath entries to compiler daemon")
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

        val response = stub!!.complete(request)

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

        val response = stub!!.listErrors(request)

        return response.diagnosticsList.map { it.fromProto() }
    }

    override suspend fun checkComplete(code: String): Boolean {
        val request =
            org.jetbrains.kotlinx.jupyter.compiler.proto.CheckCompleteRequest
                .newBuilder()
                .setCode(code)
                .build()

        val response = stub!!.checkComplete(request)

        return response.isComplete
    }

    override suspend fun getClasspath(): List<String> {
        val request =
            org.jetbrains.kotlinx.jupyter.compiler.proto.GetClasspathRequest
                .newBuilder()
                .build()

        val response = stub!!.getClasspath(request)

        return response.classpathEntriesList
    }

    private fun findDaemonJar(): File {
        // Extract the daemon JAR from resources (it's packaged with this module)
        val resourceStream = javaClass.getResourceAsStream("/compiler-daemon.jar")
            ?: throw RuntimeException("Could not find compiler-daemon.jar in resources. Please rebuild the project.")

        // Create a temporary file to hold the daemon JAR
        val tempFile = File.createTempFile("compiler-daemon-", ".jar")
        tempFile.deleteOnExit()

        resourceStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return tempFile
    }

    /**
     * Closes the daemon client, shutting down the daemon process and all connections.
     * This method is idempotent and safe to call multiple times.
     */
    override fun close() {
        try {
            // Shutdown gRPC channel
            channel?.shutdown()
            channel?.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)
            channel = null
            stub = null

            // Shutdown callback server
            callbackServer?.shutdown()
            callbackServer?.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)
            callbackServer = null

            // Kill daemon process
            daemonProcess?.destroy()
            // Wait up to 5 seconds for graceful shutdown
            if (daemonProcess?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) == false) {
                // Force kill if still running
                daemonProcess?.destroyForcibly()
            }
            daemonProcess = null

            println("Compiler daemon shut down successfully")
        } catch (e: Exception) {
            println("Error during daemon shutdown: ${e.message}")
        }
    }
}

/**
 * Implements the callback service that the daemon will call.
 */
private class KernelCallbackServiceImpl(
    private val callbacks: KernelCallbacks,
) : KernelCallbackServiceGrpcKt.KernelCallbackServiceCoroutineImplBase() {
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
        val result = callbacks.resolveDependencies(annotations)

        return when (result) {
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
}

// Extension functions to convert from proto to API types

private fun Diagnostic.fromProto(): ScriptDiagnostic =
    ScriptDiagnostic(
        code = code,
        message = message,
        severity = when (severity) {
            DiagnosticSeverity.FATAL -> ScriptDiagnostic.Severity.FATAL
            DiagnosticSeverity.ERROR -> ScriptDiagnostic.Severity.ERROR
            DiagnosticSeverity.WARNING -> ScriptDiagnostic.Severity.WARNING
            DiagnosticSeverity.INFO -> ScriptDiagnostic.Severity.INFO
            DiagnosticSeverity.DEBUG -> ScriptDiagnostic.Severity.DEBUG
            DiagnosticSeverity.UNRECOGNIZED -> error("Unrecognized DiagnosticSeverity: $severity")
        },
        sourcePath = if (hasSourcePath()) sourcePath else null,
        location = if (hasLocation()) {
            SourceCode.Location(
                start = SourceCode.Position(location.start.line, location.start.col),
                end = if (location.hasEnd()) {
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
        deprecationLevel = if (hasDeprecationLevel()) {
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
