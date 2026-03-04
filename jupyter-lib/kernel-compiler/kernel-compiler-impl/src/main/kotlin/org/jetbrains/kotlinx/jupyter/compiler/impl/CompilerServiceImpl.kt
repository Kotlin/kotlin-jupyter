package org.jetbrains.kotlinx.jupyter.compiler.impl

import jupyter.kotlin.CompilerArgs
import jupyter.kotlin.DependsOn
import jupyter.kotlin.Repository
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.DeclarationKind
import org.jetbrains.kotlinx.jupyter.compiler.CompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.DefaultCompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.ScriptDataCollector
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResult
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyAnnotation
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyResolutionResult
import org.jetbrains.kotlinx.jupyter.compiler.api.JupyterCompilerWithCompletion
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.config.CellId
import org.jetbrains.kotlinx.jupyter.config.JupyterCompilingOptions
import org.jetbrains.kotlinx.jupyter.config.getCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.SourceCodeCompletionVariant
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.collectedAnnotations
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.refineConfigurationBeforeCompiling
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.withUpdatedClasspath
import kotlin.script.experimental.util.LinkedSnippet

/**
 * In-process implementation of CompilerService.
 * This implementation uses actual Kotlin scripting APIs to compile code.
 */
class CompilerServiceImpl(
    private val params: CompilerParams,
    private val callbacks: KernelCallbacks,
    private val loggerFactory: KernelLoggerFactory,
) : CompilerService {
    private val compilerArgsConfigurator: CompilerArgsConfigurator =
        DefaultCompilerArgsConfigurator(params.jvmTarget, params.extraCompilerArguments + "-no-stdlib")

    // Script data collectors for imports and declarations
    private val importsCollector = ImportsCollector(callbacks)
    private val declarationsCollector = DeclarationsCollector(callbacks)
    private val scriptDataCollectors: List<ScriptDataCollector> =
        listOf(
            importsCollector,
            declarationsCollector,
        )

    private val compiler: JupyterCompilerWithCompletion by lazy {
        when (params.replCompilerMode) {
            org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode.K1 -> {
                JupyterCompilerFactory.createK1Compiler(
                    compilationConfig,
                )
            }
            org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode.K2 -> {
                JupyterCompilerFactory.createK2Compiler(
                    compilationConfig,
                )
            }
        }
    }

    private val compilationConfig: ScriptCompilationConfiguration =
        getCompilationConfiguration(
            scriptClasspath = params.scriptClasspath.map { File(it) },
            scriptReceiverCanonicalNames = params.scriptReceiverCanonicalNames,
            compilerArgsConfigurator = compilerArgsConfigurator,
            scriptDataCollectors = scriptDataCollectors,
            replCompilerMode = params.replCompilerMode,
            loggerFactory = loggerFactory,
        ).let { originalConfig ->
            originalConfig.with {
                refineConfiguration {
                    onAnnotations(
                        DependsOn::class,
                        Repository::class,
                        CompilerArgs::class,
                        handler = ::onAnnotationsHandler,
                    )

                    originalConfig[ScriptCompilationConfiguration.refineConfigurationBeforeCompiling]?.forEach {
                        beforeCompiling(it.handler)
                    }

                    beforeCompiling { context ->
                        context.compilationConfiguration
                            .with {
                                updateClasspath(runBlocking { callbacks.updatedClasspath().map { File(it) } })
                            }.asSuccess()
                    }
                }
            }
        }

    private fun onAnnotationsHandler(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotations =
            context.collectedData?.get(ScriptCollectedData.collectedAnnotations)?.takeIf { it.isNotEmpty() }
                ?: return context.compilationConfiguration.asSuccess()

        var config = context.compilationConfiguration
        val dependencyAnnotations = mutableListOf<DependencyAnnotation>()
        val compilerArgsAnnotations = mutableListOf<CompilerArgs>()

        // Group annotations by type
        for (annotationData in annotations) {
            when (val annotation = annotationData.annotation) {
                is DependsOn -> {
                    dependencyAnnotations.add(DependencyAnnotation.DependsOn(annotation.value))
                }
                is Repository -> {
                    dependencyAnnotations.add(DependencyAnnotation.Repository(annotation.value))
                }
                is CompilerArgs -> {
                    compilerArgsAnnotations.add(annotation)
                }
            }
        }

        if (compilerArgsAnnotations.isNotEmpty()) {
            when (val result = compilerArgsConfigurator.configure(config, compilerArgsAnnotations)) {
                is ResultWithDiagnostics.Success -> config = result.value
                is ResultWithDiagnostics.Failure -> return result
            }
        }

        if (dependencyAnnotations.isEmpty()) return config.asSuccess()

        return when (val resolution = runBlocking { callbacks.resolveDependencies(dependencyAnnotations) }) {
            is DependencyResolutionResult.Success -> {
                if (resolution.classpathEntries.isEmpty()) return config.asSuccess()

                config
                    .withUpdatedClasspath(resolution.classpathEntries.map { File(it) })
                    .asSuccess()
            }
            is DependencyResolutionResult.Failure -> {
                ResultWithDiagnostics.Failure(
                    listOf(
                        ScriptDiagnostic(
                            ScriptDiagnostic.unspecifiedError,
                            "Dependency resolution failed: ${resolution.message}",
                            severity = ScriptDiagnostic.Severity.ERROR,
                        ),
                    ),
                )
            }
        }
    }

    override suspend fun compile(
        snippetId: Int,
        code: String,
        cellId: Int,
        isUserCode: Boolean,
    ): CompileResult {
        // Compile using JupyterCompiler
        return try {
            val linkedSnippet =
                compiler.compileSync(
                    snippetId,
                    code,
                    JupyterCompilingOptions(
                        CellId(cellId),
                        isUserCode = isUserCode,
                    ),
                )

            // Convert LinkedSnippet to a serializable list of compiled scripts
            val scriptsList = mutableListOf<KJvmCompiledScript>()
            var current: LinkedSnippet<KJvmCompiledScript>? = linkedSnippet
            while (current != null) {
                scriptsList.add(current.get())
                current = current.previous
            }
            scriptsList.reverse() // Reverse to get original order

            // Compute hash codes for each script
            val hashCodes = scriptsList.map { it.hashCode() }

            // Serialize the list using Java serialization
            val serializedSnippet = serializeObject(scriptsList)

            CompileResult.Success(
                serializedCompiledSnippet = serializedSnippet,
                scriptHashCodes = hashCodes,
            )
        } catch (e: ReplCompilerException) {
            CompileResult.Failure(e.errorResult?.reports.orEmpty())
        }
    }

    private fun serializeObject(obj: Any): ByteArray {
        val baos = ByteArrayOutputStream()
        ObjectOutputStream(baos).use { oos ->
            oos.writeObject(obj)
        }
        return baos.toByteArray()
    }

    override suspend fun complete(
        code: String,
        id: Int,
        position: SourceCode.Position,
    ): List<SourceCodeCompletionVariant> =
        compiler.complete
            .complete(code, position, id)
            .valueOrNull()
            ?.toList()
            .orEmpty()

    override suspend fun listErrors(
        code: String,
        id: Int,
    ): List<ScriptDiagnostic> = compiler.listErrors(code, id).toList()

    override suspend fun checkComplete(
        code: String,
        snippetId: Int,
    ): Boolean {
        val result = compiler.checkComplete(code, snippetId)
        return result.isComplete
    }

    override suspend fun getClasspath(): List<String> = compilationConfig.classpath.map { it.absolutePath }

    private val ScriptCompilationConfiguration.classpath: List<File>
        get() =
            this[ScriptCompilationConfiguration.dependencies]
                ?.filterIsInstance<JvmDependency>()
                ?.flatMap { it.classpath }
                .orEmpty()
}

/**
 * Collector for imports that reports them via callbacks.
 */
private class ImportsCollector(
    private val callbacks: KernelCallbacks,
) : ScriptDataCollector {
    override fun collect(scriptInfo: ScriptDataCollector.ScriptInfo) {
        val source = scriptInfo.source
        if (source !is KtFileScriptSource) return

        val imports =
            source.ktFile.importDirectives.mapNotNull {
                it.importPath?.pathStr
            }

        if (imports.isNotEmpty()) {
            runBlocking {
                callbacks.reportImports(imports)
            }
        }
    }
}

/**
 * Collector for declarations that reports them via callbacks.
 */
private class DeclarationsCollector(
    private val callbacks: KernelCallbacks,
) : ScriptDataCollector {
    override fun collect(scriptInfo: ScriptDataCollector.ScriptInfo) {
        if (!scriptInfo.isUserScript) return
        val source = scriptInfo.source
        if (source !is KtFileScriptSource) return

        val fileDeclarations = source.ktFile.declarations
        val scriptDeclaration = fileDeclarations.getOrNull(0) as? KtScript ?: return

        val declarations =
            scriptDeclaration.declarations.map { declaration ->
                val kind =
                    when (declaration) {
                        is KtClass -> DeclarationKind.CLASS
                        is KtObjectDeclaration -> DeclarationKind.OBJECT
                        is KtProperty -> DeclarationKind.PROPERTY
                        is KtFunction -> DeclarationKind.FUNCTION
                        is KtScriptInitializer -> DeclarationKind.SCRIPT_INITIALIZER
                        else -> DeclarationKind.UNKNOWN
                    }

                SimpleDeclarationInfo(
                    name = declaration.name,
                    kind = kind,
                )
            }

        runBlocking {
            callbacks.reportDeclarations(declarations)
        }
    }
}

/**
 * Simple implementation of DeclarationInfo.
 */
private data class SimpleDeclarationInfo(
    override val name: String?,
    override val kind: DeclarationKind,
) : DeclarationInfo
