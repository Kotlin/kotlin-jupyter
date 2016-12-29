package org.jetbrains.kotlin.jupyter

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.jvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.repl.GenericReplCompiler
import org.jetbrains.kotlin.com.google.common.base.Throwables
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.script.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.script.util.templates.StandardArgsScriptTemplateWithMavenResolving
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File
import java.net.URLClassLoader


class ReplForJupyter(val conn: JupyterConnection) {
    private val requiredKotlinLibraries = listOf(
            StandardArgsScriptTemplateWithMavenResolving::class.containingClasspath() ?:
                    throw IllegalStateException("Cannot find template classpath, which is required"),
            GenericReplCompiler::class.containingClasspath() ?:
                    throw IllegalStateException("Cannot find repl engine classpath, which is required"),
            KotlinCompilerVersion::class.containingClasspath() ?:
                    throw IllegalStateException("Cannot find kotlin compiler classpath, which is required"),
            Pair::class.containingClasspath() ?:
                    throw IllegalStateException("Cannot find kotlin stdlib classpath, which is required"),
            JvmName::class.containingClasspath() ?:
                    throw IllegalStateException("Cannot find kotlin runtime classpath, which is required")
    ).toSet().toList()

    private val compilerConfiguration = CompilerConfiguration().apply {
        addJvmClasspathRoots(PathUtil.getJdkClassesRoots())
        addJvmClasspathRoots(requiredKotlinLibraries)
        addJvmClasspathRoots(conn.config.classpath)
        put(CommonConfigurationKeys.MODULE_NAME, "jupyter")
        put<MessageCollector>(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, PrintingMessageCollector(conn.iopubErr, MessageRenderer.WITHOUT_PATHS, false))
        put(JVMConfigurationKeys.INCLUDE_RUNTIME, true)
    }

    val classpath = compilerConfiguration.jvmClasspathRoots.toMutableList()
    private val baseClassloader = URLClassLoader(classpath.map { it.toURI().toURL() }.toTypedArray(), Thread.currentThread().contextClassLoader)

    private val scriptDef = KotlinScriptDefinitionFromAnnotatedTemplate(StandardArgsScriptTemplateWithMavenResolving::class, null, null, emptyMap())

    val replCompiler: GenericReplCompiler by lazy {
        GenericReplCompiler(conn.disposable, scriptDef, compilerConfiguration, PrintingMessageCollector(System.out, MessageRenderer.WITHOUT_PATHS, false))
    }

    val compiledEvaluator: GenericReplCompiledEvaluator by lazy {
        GenericReplCompiledEvaluator(compilerConfiguration.jvmClasspathRoots, baseClassloader, arrayOf(emptyArray<String>()))
    }


    private var earlierLines: List<ReplCodeLine> = arrayListOf<ReplCodeLine>()

    private fun makeCodeLine(executionNumber: Long, code: String): ReplCodeLine {
        return ReplCodeLine(executionNumber.toInt(), code) /* TODO: check this toInt() isn't deadly */
    }

    private fun findEarlierLines(codeLine: ReplCodeLine): List<ReplCodeLine> {
        return earlierLines.filter { it.no < codeLine.no }
    }

    fun checkComplete(executionNumber: Long, code: String): ReplCheckResult {
        val codeLine = makeCodeLine(executionNumber, code)
        return replCompiler.check(codeLine, findEarlierLines(codeLine))
    }

    fun eval(executionNumber: Long, code: String): ReplEvalResult {
        synchronized(this) {
            val codeLine = makeCodeLine(executionNumber, code)
            val check = replCompiler.check(codeLine, findEarlierLines(codeLine))
            when (check) {
                is ReplCheckResult.Ok -> {
                } // nop
                is ReplCheckResult.Incomplete -> return ReplEvalResult.Incomplete(check.updatedHistory)
                is ReplCheckResult.Error -> return ReplEvalResult.Error.CompileTime(check.updatedHistory, check.message, check.location)
            }

            val compile = replCompiler.compile(codeLine, earlierLines)
            when (compile) {
                is ReplCompileResult.Incomplete -> return ReplEvalResult.Incomplete(compile.updatedHistory)
                is ReplCompileResult.HistoryMismatch -> return ReplEvalResult.HistoryMismatch(compile.updatedHistory, compile.lineNo)
                is ReplCompileResult.Error -> return ReplEvalResult.Error.CompileTime(compile.updatedHistory, compile.message, compile.location)
                is ReplCompileResult.CompiledClasses -> {
                } // nop
            }

            val successfulCompilation = compile as ReplCompileResult.CompiledClasses

            val eval = compiledEvaluator.eval(codeLine, earlierLines, successfulCompilation.classes,
                    successfulCompilation.hasResult,
                    successfulCompilation.classpathAddendum)

            earlierLines = check.updatedHistory
            return eval
        }
    }

    init {
        log.info("Starting kotlin repl ${KotlinCompilerVersion.VERSION}")
        log.info("Using classpath:\n${classpath.joinToString("\n") { it.canonicalPath }}")
    }

    companion object {
        private val SCRIPT_RESULT_FIELD_NAME = "\$\$result"

        private fun renderStackTrace(cause: Throwable, startFromMethodName: String): String {
            val newTrace = arrayListOf<StackTraceElement>()
            var skip = true
            for ((i, element) in cause.stackTrace.withIndex().reversed()) {
                if ("${element.className}.${element.methodName}" == startFromMethodName) {
                    skip = false
                }
                if (!skip) {
                    newTrace.add(element)
                }
            }

            val resultingTrace = newTrace.reversed().dropLast(1)

            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UsePropertyAccessSyntax")
            (cause as java.lang.Throwable).setStackTrace(resultingTrace.toTypedArray())

            return Throwables.getStackTraceAsString(cause)
        }
    }
}


