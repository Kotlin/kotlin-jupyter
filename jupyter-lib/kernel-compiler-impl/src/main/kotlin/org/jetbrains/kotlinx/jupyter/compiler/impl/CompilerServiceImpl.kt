package org.jetbrains.kotlinx.jupyter.compiler.impl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerBase
import org.jetbrains.kotlin.scripting.compiler.plugin.repl.ReplCodeAnalyzerBase
import org.jetbrains.kotlinx.jupyter.api.DEFAULT
import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.DeclarationKind
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.compiler.CompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.DefaultCompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.compiler.ScriptDataCollector
import org.jetbrains.kotlinx.jupyter.compiler.api.CompileResult
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerService
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyAnnotation
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyResolutionResult
import org.jetbrains.kotlinx.jupyter.compiler.api.Diagnostic
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.compiler.getCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.impl.getOrCreateActualClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.lastSnippetClassLoader
import kotlin.script.experimental.jvm.updateClasspath

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
    private val scriptDataCollectors: List<ScriptDataCollector> = listOf(
        ImportsCollector(),
        DeclarationsCollector(),
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
            loggerFactory = DummyLoggerFactory,
        )

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

    private fun updateClasspath(classpathEntries: List<String>) {
        currentClasspath.addAll(classpathEntries.map { File(it) })
        // Recreate compilation config with updated classpath
        compilationConfig = createCompilationConfig()
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
    compilationConfiguration: ScriptCompilationConfiguration,
) : KJvmReplCompilerBase<ReplCodeAnalyzerBase>(compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]!!)

/**
 * Simple implementation of DeclarationInfo for extracted declarations.
 */
private data class SimpleDeclarationInfo(
    override val name: String?,
    override val kind: DeclarationKind,
) : DeclarationInfo

/**
 * Collector for imports (currently no-op as imports are extracted separately).
 */
private class ImportsCollector : ScriptDataCollector {
    override fun collect(scriptInfo: ScriptDataCollector.ScriptInfo) {
        // No-op: imports are reported via callbacks separately
    }
}

/**
 * Collector for declarations (currently no-op as declarations are extracted separately).
 */
private class DeclarationsCollector : ScriptDataCollector {
    override fun collect(scriptInfo: ScriptDataCollector.ScriptInfo) {
        // No-op: declarations are reported via callbacks separately
    }
}

/**
 * Dummy logger factory for daemon-side compilation.
 */
private object DummyLoggerFactory : KernelLoggerFactory {
    private val dummyLogger = object : Logger {
        override fun getName() = "DummyLogger"
        override fun isTraceEnabled() = false
        override fun isTraceEnabled(marker: org.slf4j.Marker?) = false
        override fun trace(msg: String?) {}
        override fun trace(format: String?, arg: Any?) {}
        override fun trace(format: String?, arg1: Any?, arg2: Any?) {}
        override fun trace(format: String?, vararg arguments: Any?) {}
        override fun trace(msg: String?, t: Throwable?) {}
        override fun trace(marker: org.slf4j.Marker?, msg: String?) {}
        override fun trace(marker: org.slf4j.Marker?, format: String?, arg: Any?) {}
        override fun trace(marker: org.slf4j.Marker?, format: String?, arg1: Any?, arg2: Any?) {}
        override fun trace(marker: org.slf4j.Marker?, format: String?, vararg argArray: Any?) {}
        override fun trace(marker: org.slf4j.Marker?, msg: String?, t: Throwable?) {}
        override fun isDebugEnabled() = false
        override fun isDebugEnabled(marker: org.slf4j.Marker?) = false
        override fun debug(msg: String?) {}
        override fun debug(format: String?, arg: Any?) {}
        override fun debug(format: String?, arg1: Any?, arg2: Any?) {}
        override fun debug(format: String?, vararg arguments: Any?) {}
        override fun debug(msg: String?, t: Throwable?) {}
        override fun debug(marker: org.slf4j.Marker?, msg: String?) {}
        override fun debug(marker: org.slf4j.Marker?, format: String?, arg: Any?) {}
        override fun debug(marker: org.slf4j.Marker?, format: String?, arg1: Any?, arg2: Any?) {}
        override fun debug(marker: org.slf4j.Marker?, format: String?, vararg argArray: Any?) {}
        override fun debug(marker: org.slf4j.Marker?, msg: String?, t: Throwable?) {}
        override fun isInfoEnabled() = false
        override fun isInfoEnabled(marker: org.slf4j.Marker?) = false
        override fun info(msg: String?) {}
        override fun info(format: String?, arg: Any?) {}
        override fun info(format: String?, arg1: Any?, arg2: Any?) {}
        override fun info(format: String?, vararg arguments: Any?) {}
        override fun info(msg: String?, t: Throwable?) {}
        override fun info(marker: org.slf4j.Marker?, msg: String?) {}
        override fun info(marker: org.slf4j.Marker?, format: String?, arg: Any?) {}
        override fun info(marker: org.slf4j.Marker?, format: String?, arg1: Any?, arg2: Any?) {}
        override fun info(marker: org.slf4j.Marker?, format: String?, vararg argArray: Any?) {}
        override fun info(marker: org.slf4j.Marker?, msg: String?, t: Throwable?) {}
        override fun isWarnEnabled() = false
        override fun isWarnEnabled(marker: org.slf4j.Marker?) = false
        override fun warn(msg: String?) {}
        override fun warn(format: String?, arg: Any?) {}
        override fun warn(format: String?, vararg arguments: Any?) {}
        override fun warn(format: String?, arg1: Any?, arg2: Any?) {}
        override fun warn(msg: String?, t: Throwable?) {}
        override fun warn(marker: org.slf4j.Marker?, msg: String?) {}
        override fun warn(marker: org.slf4j.Marker?, format: String?, arg: Any?) {}
        override fun warn(marker: org.slf4j.Marker?, format: String?, arg1: Any?, arg2: Any?) {}
        override fun warn(marker: org.slf4j.Marker?, format: String?, vararg argArray: Any?) {}
        override fun warn(marker: org.slf4j.Marker?, msg: String?, t: Throwable?) {}
        override fun isErrorEnabled() = false
        override fun isErrorEnabled(marker: org.slf4j.Marker?) = false
        override fun error(msg: String?) {}
        override fun error(format: String?, arg: Any?) {}
        override fun error(format: String?, arg1: Any?, arg2: Any?) {}
        override fun error(format: String?, vararg arguments: Any?) {}
        override fun error(msg: String?, t: Throwable?) {}
        override fun error(marker: org.slf4j.Marker?, msg: String?) {}
        override fun error(marker: org.slf4j.Marker?, format: String?, arg: Any?) {}
        override fun error(marker: org.slf4j.Marker?, format: String?, arg1: Any?, arg2: Any?) {}
        override fun error(marker: org.slf4j.Marker?, format: String?, vararg argArray: Any?) {}
        override fun error(marker: org.slf4j.Marker?, msg: String?, t: Throwable?) {}
    }

    override fun getLogger(name: String) = dummyLogger
    override fun getLogger(clazz: Class<*>) = dummyLogger
}
