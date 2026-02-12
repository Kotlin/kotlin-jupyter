package org.jetbrains.kotlinx.jupyter.compiler.impl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerBase
import org.jetbrains.kotlin.scripting.compiler.plugin.repl.ReplCodeAnalyzerBase
import org.jetbrains.kotlinx.jupyter.api.DEFAULT
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.compiler.CompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.DefaultCompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.compiler.ScriptDataCollector
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResult
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyAnnotation
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyResolutionResult
import org.jetbrains.kotlinx.jupyter.compiler.api.Diagnostic
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.config.getCompilationConfiguration
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.collectedAnnotations
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.impl.getOrCreateActualClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.lastSnippetClassLoader
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.DeclarationKind

/**
 * In-process implementation of CompilerService.
 * This implementation uses actual Kotlin scripting APIs to compile code.
 */
class CompilerServiceImpl(
    private val params: CompilerParams,
    private val callbacks: KernelCallbacks,
) : CompilerService {
    private val currentClasspath: MutableList<File> = params.scriptClasspath.map { File(it) }.toMutableList()
    private var lastClassLoader: ClassLoader = Thread.currentThread().contextClassLoader

    private val compilerArgsConfigurator: CompilerArgsConfigurator = DefaultCompilerArgsConfigurator(params.jvmTarget)

    // Script data collectors for imports and declarations
    private val importsCollector = ImportsCollector(callbacks)
    private val declarationsCollector = DeclarationsCollector(callbacks)
    private val scriptDataCollectors: List<ScriptDataCollector> = listOf(
        importsCollector,
        declarationsCollector,
    )

    private val compiler: KJvmReplCompilerBase<ReplCodeAnalyzerBase> by lazy {
        SimpleReplCompiler(compilationConfig)
    }

    // Use getCompilationConfiguration the same way as ReplForJupyterImpl
    private var compilationConfig: ScriptCompilationConfiguration = createCompilationConfig()
    private var evaluationConfig: ScriptEvaluationConfiguration = createEvaluationConfig()

    private fun createCompilationConfig(): ScriptCompilationConfiguration =
        getCompilationConfiguration(
            scriptClasspath = currentClasspath,
            scriptReceiverCanonicalNames = params.scriptReceiverCanonicalNames,
            compilerArgsConfigurator = compilerArgsConfigurator,
            scriptDataCollectors = scriptDataCollectors,
            replCompilerMode = ReplCompilerMode.DEFAULT,
            loggerFactory = DefaultKernelLoggerFactory,
        ).with {
            refineConfiguration {
                onAnnotations(jupyter.kotlin.DependsOn::class, jupyter.kotlin.Repository::class, jupyter.kotlin.CompilerArgs::class, handler = ::onAnnotationsHandler)
            }
        }

    private fun createEvaluationConfig(): ScriptEvaluationConfiguration =
        ScriptEvaluationConfiguration {
            jvm {
                baseClassLoader(lastClassLoader)
            }
        }

    private fun onAnnotationsHandler(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)?.takeIf { it.isNotEmpty() }
            ?: return context.compilationConfiguration.asSuccess()

        var config = context.compilationConfiguration
        val dependencyAnnotations = mutableListOf<DependencyAnnotation>()
        val compilerArgsAnnotations = mutableListOf<Annotation>()

        // Group annotations by type
        for (annotationData in annotations) {
            val annotation = annotationData.annotation
            when (annotation::class) {
                jupyter.kotlin.DependsOn::class -> {
                    val dependsOn = annotation as jupyter.kotlin.DependsOn
                    dependencyAnnotations.add(DependencyAnnotation.DependsOn(dependsOn.value))
                }
                jupyter.kotlin.Repository::class -> {
                    val repository = annotation as jupyter.kotlin.Repository
                    dependencyAnnotations.add(DependencyAnnotation.Repository(repository.value))
                }
                jupyter.kotlin.CompilerArgs::class -> {
                    compilerArgsAnnotations.add(annotation)
                }
            }
        }

        // Handle CompilerArgs annotations
        if (compilerArgsAnnotations.isNotEmpty()) {
            when (val result = compilerArgsConfigurator.configure(config, compilerArgsAnnotations)) {
                is ResultWithDiagnostics.Success -> config = result.value
                is ResultWithDiagnostics.Failure -> return result
            }
        }

        // Handle dependency annotations
        if (dependencyAnnotations.isNotEmpty()) {
            runBlocking {
                when (val resolution = callbacks.resolveDependencies(dependencyAnnotations)) {
                    is DependencyResolutionResult.Success -> {
                        updateClasspath(resolution.classpathEntries)
                    }
                    is DependencyResolutionResult.Failure -> {
                        // Return failure - will be caught during compilation
                        return@runBlocking ResultWithDiagnostics.Failure(
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
        }

        return config.asSuccess()
    }

    override suspend fun compile(
        snippetId: Int,
        code: String,
        cellId: Int,
    ): CompileResult {
        // Create source code using the same naming convention as SourceCodeImpl
        val source = "Line_$snippetId".toScriptSource(code)

        // Compile
        return when (val result = compiler.compile(source, compilationConfig)) {
            is ResultWithDiagnostics.Failure -> {
                CompileResult.Failure(
                    result.reports.map { it.toDiagnostic() },
                )
            }
            is ResultWithDiagnostics.Success -> {
                val linkedSnippet = result.value

                // Update classloader for next compilation
                val compiledScript = linkedSnippet.get()
                val configWithClassloader = evaluationConfig.with {
                    jvm {
                        lastSnippetClassLoader(lastClassLoader)
                    }
                }
                lastClassLoader = compiledScript.getOrCreateActualClassloader(configWithClassloader)

                // Create updated evaluation configuration
                val newEvalConfig = configWithClassloader.with {
                    jvm {
                        baseClassLoader(lastClassLoader)
                    }
                }

                // Serialize using Java serialization
                val serializedSnippet = serializeObject(linkedSnippet)
                val serializedEvalConfig = serializeObject(newEvalConfig)

                CompileResult.Success(
                    serializedCompiledSnippet = serializedSnippet,
                    serializedEvalConfig = serializedEvalConfig,
                )
            }
        }
    }

    private fun serializeObject(obj: Any): ByteArray {
        val baos = ByteArrayOutputStream()
        ObjectOutputStream(baos).use { oos ->
            oos.writeObject(obj)
        }
        return baos.toByteArray()
    }

    private fun updateClasspath(classpathEntries: List<String>) {
        currentClasspath.addAll(classpathEntries.map { File(it) })
        // Recreate compilation config with updated classpath
        compilationConfig = createCompilationConfig()
    }

    private fun ScriptDiagnostic.toDiagnostic(): Diagnostic =
        Diagnostic(
            severity = when (this.severity) {
                ScriptDiagnostic.Severity.FATAL, ScriptDiagnostic.Severity.ERROR -> Diagnostic.Severity.ERROR
                ScriptDiagnostic.Severity.WARNING -> Diagnostic.Severity.WARNING
                else -> Diagnostic.Severity.INFO
            },
            message = this.message,
            line = this.location?.start?.line,
            column = this.location?.start?.col,
        )
}

/**
 * Simple REPL compiler using Kotlin scripting APIs.
 */
private class SimpleReplCompiler(
    compilationConfiguration: ScriptCompilationConfiguration,
) : KJvmReplCompilerBase<ReplCodeAnalyzerBase>(compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]!!)

/**
 * Collector for imports that reports them via callbacks.
 */
private class ImportsCollector(private val callbacks: KernelCallbacks) : ScriptDataCollector {
    override fun collect(scriptInfo: ScriptDataCollector.ScriptInfo) {
        val source = scriptInfo.source
        if (source !is KtFileScriptSource) return

        val imports = source.ktFile.importDirectives.mapNotNull {
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
private class DeclarationsCollector(private val callbacks: KernelCallbacks) : ScriptDataCollector {
    override fun collect(scriptInfo: ScriptDataCollector.ScriptInfo) {
        if (!scriptInfo.isUserScript) return
        val source = scriptInfo.source
        if (source !is KtFileScriptSource) return

        val fileDeclarations = source.ktFile.declarations
        val scriptDeclaration = fileDeclarations.getOrNull(0) as? KtScript ?: return

        val declarations = scriptDeclaration.declarations.map { declaration ->
            val kind = when (declaration) {
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
