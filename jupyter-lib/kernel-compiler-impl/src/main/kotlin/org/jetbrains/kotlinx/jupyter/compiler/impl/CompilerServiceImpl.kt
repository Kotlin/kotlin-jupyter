package org.jetbrains.kotlinx.jupyter.compiler.impl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerBase
import org.jetbrains.kotlin.scripting.compiler.plugin.repl.ReplCodeAnalyzerBase
import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.DeclarationKind
import org.jetbrains.kotlinx.jupyter.compiler.api.CheckCompleteResult
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResult
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.CompleteResult
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyAnnotation
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyResolutionResult
import org.jetbrains.kotlinx.jupyter.compiler.api.Diagnostic
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.impl.getOrCreateActualClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.lastSnippetClassLoader
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.util.LinkedSnippet

/**
 * In-process implementation of CompilerService.
 * This implementation uses actual Kotlin scripting APIs to compile code.
 */
class CompilerServiceImpl(
    private val params: CompilerParams,
    private val callbacks: KernelCallbacks,
) : CompilerService {
    private val currentClasspath: MutableList<String> = params.scriptClasspath.toMutableList()
    private var lastClassLoader: ClassLoader = Thread.currentThread().contextClassLoader

    private val compiler: KJvmReplCompilerBase<ReplCodeAnalyzerBase> by lazy {
        SimpleReplCompiler(defaultJvmScriptingHostConfiguration)
    }

    private var compilationConfig: ScriptCompilationConfiguration = createCompilationConfig()
    private var evaluationConfig: ScriptEvaluationConfiguration = createEvaluationConfig()

    private fun createCompilationConfig(): ScriptCompilationConfiguration =
        ScriptCompilationConfiguration {
            hostConfiguration(defaultJvmScriptingHostConfiguration)
            jvm {
                updateClasspath(currentClasspath.map { File(it) })
            }
        }

    private fun createEvaluationConfig(): ScriptEvaluationConfiguration =
        ScriptEvaluationConfiguration {
            jvm {
                baseClassLoader(lastClassLoader)
            }
        }

    override suspend fun compile(
        snippetId: Int,
        code: String,
        cellId: Int,
    ): CompileResult {
        // Report imports and declarations
        val imports = extractImports(code)
        if (imports.isNotEmpty()) {
            callbacks.reportImports(imports)
        }

        val declarations = extractDeclarations(code)
        if (declarations.isNotEmpty()) {
            callbacks.reportDeclarations(declarations)
        }

        // Handle dependency annotations
        val dependencyAnnotations = extractDependencyAnnotations(code)
        if (dependencyAnnotations.isNotEmpty()) {
            when (val resolution = callbacks.resolveDependencies(dependencyAnnotations)) {
                is DependencyResolutionResult.Success -> {
                    updateClasspath(resolution.classpathEntries)
                }
                is DependencyResolutionResult.Failure -> {
                    return CompileResult.Failure(
                        listOf(
                            Diagnostic(
                                Diagnostic.Severity.ERROR,
                                "Dependency resolution failed: ${resolution.message}",
                            ),
                        ),
                    )
                }
            }
        }

        // Create source code using the same naming convention as SourceCodeImpl
        val source = "Line_$snippetId".toScriptSource(code)

        // Compile
        return when (val result = runBlocking { compiler.compile(source, compilationConfig) }) {
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

    override suspend fun updateClasspath(classpathEntries: List<String>) {
        currentClasspath.addAll(classpathEntries)
        // Recreate compilation config with updated classpath
        compilationConfig = createCompilationConfig()
    }

    override suspend fun checkComplete(code: String): CheckCompleteResult {
        val trimmed = code.trim()
        val isIncomplete =
            trimmed.endsWith("{") ||
                trimmed.endsWith("(") ||
                trimmed.endsWith(",") ||
                trimmed.endsWith("=") ||
                (trimmed.count { it == '{' } > trimmed.count { it == '}' }) ||
                (trimmed.count { it == '(' } > trimmed.count { it == ')' })

        return CheckCompleteResult(isComplete = !isIncomplete)
    }

    override suspend fun listErrors(code: String): List<Diagnostic> {
        // Compile the code to get diagnostics without executing
        val source = "error_check".toScriptSource(code)
        return when (val result = runBlocking { compiler.compile(source, compilationConfig) }) {
            is ResultWithDiagnostics.Failure -> {
                result.reports.map { it.toDiagnostic() }
            }
            is ResultWithDiagnostics.Success -> {
                // Check if there are any warnings
                result.reports.map { it.toDiagnostic() }
            }
        }
    }

    override suspend fun complete(
        code: String,
        cursor: Int,
    ): CompleteResult {
        // Code completion would require integration with Kotlin completion API
        // For now, return empty results - this functionality is typically
        // handled by the IDE/editor rather than the REPL compiler
        return CompleteResult(
            items = emptyList(),
            cursorStart = cursor,
            cursorEnd = cursor,
        )
    }

    override suspend fun shutdown() {
        // No cleanup needed for in-process compiler
    }

    private fun extractImports(code: String): List<String> {
        val importRegex = Regex("""^\s*import\s+(.+)""", RegexOption.MULTILINE)
        return importRegex.findAll(code).map { it.groupValues[1].trim() }.toList()
    }

    private fun extractDeclarations(code: String): List<DeclarationInfo> {
        val declarations = mutableListOf<DeclarationInfo>()

        // Extract functions
        val functionRegex = Regex("""^\s*fun\s+(\w+)""", RegexOption.MULTILINE)
        functionRegex.findAll(code).forEach { match ->
            declarations.add(
                SimpleDeclarationInfo(
                    name = match.groupValues[1],
                    kind = DeclarationKind.FUNCTION,
                ),
            )
        }

        // Extract classes
        val classRegex = Regex("""^\s*(class|interface)\s+(\w+)""", RegexOption.MULTILINE)
        classRegex.findAll(code).forEach { match ->
            declarations.add(
                SimpleDeclarationInfo(
                    name = match.groupValues[2],
                    kind = DeclarationKind.CLASS,
                ),
            )
        }

        // Extract objects
        val objectRegex = Regex("""^\s*object\s+(\w+)""", RegexOption.MULTILINE)
        objectRegex.findAll(code).forEach { match ->
            declarations.add(
                SimpleDeclarationInfo(
                    name = match.groupValues[1],
                    kind = DeclarationKind.OBJECT,
                ),
            )
        }

        // Extract properties
        val propertyRegex = Regex("""^\s*(val|var)\s+(\w+)""", RegexOption.MULTILINE)
        propertyRegex.findAll(code).forEach { match ->
            declarations.add(
                SimpleDeclarationInfo(
                    name = match.groupValues[2],
                    kind = DeclarationKind.PROPERTY,
                ),
            )
        }

        return declarations
    }

    private fun extractDependencyAnnotations(code: String): List<DependencyAnnotation> {
        val annotations = mutableListOf<DependencyAnnotation>()

        val dependsOnRegex = Regex("""@DependsOn\s*\(\s*"([^"]+)"\s*\)""")
        dependsOnRegex.findAll(code).forEach { match ->
            annotations.add(DependencyAnnotation.DependsOn(match.groupValues[1]))
        }

        val repositoryRegex = Regex("""@Repository\s*\(\s*"([^"]+)"\s*\)""")
        repositoryRegex.findAll(code).forEach { match ->
            annotations.add(DependencyAnnotation.Repository(match.groupValues[1]))
        }

        return annotations
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
    hostConfiguration: ScriptingHostConfiguration,
) : KJvmReplCompilerBase<ReplCodeAnalyzerBase>(hostConfiguration)

/**
 * Simple implementation of DeclarationInfo for extracted declarations.
 */
private data class SimpleDeclarationInfo(
    override val name: String?,
    override val kind: DeclarationKind,
) : DeclarationInfo
