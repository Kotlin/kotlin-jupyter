package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.MimeTypedResult
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import uy.kohesive.keplin.kotlin.script.CheckResult
import uy.kohesive.keplin.kotlin.script.EvalResult
import uy.kohesive.keplin.kotlin.script.SimplifiedRepl
import uy.kohesive.keplin.kotlin.script.resolver.AnnotationTriggeredScriptDefinition
import uy.kohesive.keplin.kotlin.script.resolver.JarFileScriptDependenciesResolver
import uy.kohesive.keplin.kotlin.script.resolver.maven.MavenScriptDependenciesResolver
import uy.kohesive.keplin.util.ClassPathUtils.findClassJars
import uy.kohesive.keplin.util.assertNotEmpty
import kotlin.reflect.KClass


class ReplForJupyter(val conn: JupyterConnection) {
    private val EMPTY_SCRIPT_ARGS: Array<out Any?> = arrayOf(emptyArray<String>())
    private val EMPTY_SCRIPT_ARGS_TYPES: Array<out KClass<out Any>> = arrayOf(Array<String>::class)
    private val engine = SimplifiedRepl(scriptDefinition = AnnotationTriggeredScriptDefinition(
            "varargTemplateWithMavenResolving",
            ScriptTemplateWithDisplayHelpers::class,
            ScriptArgsWithTypes(EMPTY_SCRIPT_ARGS, EMPTY_SCRIPT_ARGS_TYPES),
            listOf(JarFileScriptDependenciesResolver(), MavenScriptDependenciesResolver())),
            additionalClasspath = conn.config.classpath + findClassJars(MimeTypedResult::class).assertNotEmpty("Must have MimeTypedResult in classpath"),
            sharedHostClassLoader = null
    )

    fun checkComplete(executionNumber: Long, code: String): CheckResult {
        val codeLine = ReplCodeLine(executionNumber.toInt(), 0, code)
        return engine.check(codeLine)
    }

    val classpath: List<String> get() = engine.currentEvalClassPath.map { it.toString() }

    fun eval(executionNumber: Long, code: String): EvalResult {
        synchronized(this) {
            val codeLine = ReplCodeLine(executionNumber.toInt(), 0, code)
            val result = engine.compileAndEval(codeLine)
            return result
        }
    }

    init {
        log.info("Starting kotlin repl ${KotlinCompilerVersion.VERSION}")
        log.info("Using classpath:\n${engine.currentEvalClassPath.joinToString("\n") { it.canonicalPath }}")
    }
}

