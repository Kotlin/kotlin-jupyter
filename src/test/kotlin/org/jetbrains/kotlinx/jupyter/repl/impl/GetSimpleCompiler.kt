package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerBase
import org.jetbrains.kotlin.scripting.compiler.plugin.repl.ReplCodeAnalyzerBase
import org.jetbrains.kotlinx.jupyter.compiler.impl.JupyterCompiler
import org.jetbrains.kotlinx.jupyter.compiler.impl.JupyterCompilerImpl
import kotlin.script.experimental.api.ReplAnalyzerResult
import kotlin.script.experimental.api.ReplCodeAnalyzer
import kotlin.script.experimental.api.ReplCompleter
import kotlin.script.experimental.api.ReplCompletionResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

internal fun getSimpleCompiler(compilationConfiguration: ScriptCompilationConfiguration): JupyterCompiler {
    class SimpleReplCompiler(
        hostConfiguration: ScriptingHostConfiguration,
    ) : KJvmReplCompilerBase<ReplCodeAnalyzerBase>(hostConfiguration),
        ReplCompleter,
        ReplCodeAnalyzer {
        override suspend fun complete(
            snippet: SourceCode,
            cursor: SourceCode.Position,
            configuration: ScriptCompilationConfiguration,
        ): ResultWithDiagnostics<ReplCompletionResult> = emptySequence<Nothing>().asSuccess()

        override suspend fun analyze(
            snippet: SourceCode,
            cursor: SourceCode.Position,
            configuration: ScriptCompilationConfiguration,
        ): ResultWithDiagnostics<ReplAnalyzerResult> = ReplAnalyzerResult {}.asSuccess()
    }

    return JupyterCompilerImpl(
        SimpleReplCompiler(
            compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]
                ?: defaultJvmScriptingHostConfiguration,
        ),
        compilationConfiguration,
    )
}
