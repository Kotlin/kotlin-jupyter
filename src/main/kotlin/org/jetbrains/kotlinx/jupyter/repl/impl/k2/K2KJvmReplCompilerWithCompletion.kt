/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.jupyter.repl.impl.k2

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.K2ReplCompilationState
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.K2ReplCompiler
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptDiagnosticsMessageCollector
import org.jetbrains.kotlin.scripting.compiler.plugin.repl.writer.ConsoleReplWriter
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.slf4j.Logger
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.ReplAnalyzerResult
import kotlin.script.experimental.api.ReplCodeAnalyzer
import kotlin.script.experimental.api.ReplCompiler
import kotlin.script.experimental.api.ReplCompleter
import kotlin.script.experimental.api.ReplCompletionResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptCompiler
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.util.LinkedSnippet

/**
 * K2 variant of [org.jetbrains.kotlin.scripting.ide_services.compiler.KJvmReplCompilerWithIdeServices].
 *
 * Note, IDE integration and analysis does not work yet and API needs to be
 * redesigned (most likely).
 *
 * Once done it should be move back to the Kotlin repository.
 */
class K2KJvmReplCompilerWithCompletion(
    private val hostConfiguration: ScriptingHostConfiguration = defaultJvmScriptingHostConfiguration,
    private val compilerConfiguration: ScriptCompilationConfiguration = ScriptCompilationConfiguration(),
) : ReplCompiler<KJvmCompiledScript>,
    ScriptCompiler,
    ReplCompleter,
    ReplCodeAnalyzer {
    private val state: K2ReplCompilationState
    private val compiler: K2ReplCompiler

    init {
        val messageCollector = ScriptDiagnosticsMessageCollector(parentMessageCollector = null) // ReplMessageCollector()
        val rootDisposable = Disposer.newDisposable("K2KJvmReplCompilerWithCompletion rootDisposable")
        state =
            K2ReplCompiler.createCompilationState(
                messageCollector,
                rootDisposable,
                scriptCompilationConfiguration = compilerConfiguration,
                hostConfiguration = hostConfiguration,
            )
        compiler = K2ReplCompiler(state)
    }

    @Suppress("UNCHECKED_CAST")
    override val lastCompiledSnippet: LinkedSnippet<KJvmCompiledScript>?
        get() = compiler.lastCompiledSnippet as LinkedSnippet<KJvmCompiledScript>?

    override suspend fun compile(
        snippets: Iterable<SourceCode>,
        configuration: ScriptCompilationConfiguration,
    ): ResultWithDiagnostics<LinkedSnippet<KJvmCompiledScript>> {
        @Suppress("UNCHECKED_CAST")
        return compiler.compile(snippets, configuration) as ResultWithDiagnostics<LinkedSnippet<KJvmCompiledScript>>
    }

    override suspend fun invoke(
        script: SourceCode,
        scriptCompilationConfiguration: ScriptCompilationConfiguration,
    ): ResultWithDiagnostics<CompiledScript> =
        when (val res = compile(script, scriptCompilationConfiguration)) {
            is ResultWithDiagnostics.Success -> res.value.get().asSuccess(res.reports)
            is ResultWithDiagnostics.Failure -> res
        }

    override suspend fun complete(
        snippet: SourceCode,
        cursor: SourceCode.Position,
        configuration: ScriptCompilationConfiguration,
    ): ResultWithDiagnostics<ReplCompletionResult> {
        // TODO https://youtrack.jetbrains.com/issue/KTNB-916
        //  Until KTNB-916 is implemented, we just return empty completion results here.
        return ResultWithDiagnostics.Success(emptySequence(), emptyList())
    }

    override suspend fun analyze(
        snippet: SourceCode,
        cursor: SourceCode.Position,
        configuration: ScriptCompilationConfiguration,
    ): ResultWithDiagnostics<ReplAnalyzerResult> {
        // TODO https://youtrack.jetbrains.com/issue/KTNB-916
        //  Until KTNB-916 is implemented, we just return empty analysis results here.
        return ReplAnalyzerResult {}.asSuccess()
    }
}

/**
 * Class responsible for handling messages from the underlying Kotlin Compiler.
 * Currently, it will forward them to the kernel logs.
 * TODO Unclear if we need this. Keep it around for now.
 */
private class ReplMessageCollector : MessageCollector {
    val replWriter = ConsoleReplWriter()
    private val logger: Logger = DefaultKernelLoggerFactory.getLogger(this::class.java)
    private var hasErrors = false
    private val messageRenderer = MessageRenderer.WITHOUT_PATHS

    override fun clear() {
        hasErrors = false
    }

    override fun report(
        severity: CompilerMessageSeverity,
        message: String,
        location: CompilerMessageSourceLocation?,
    ) {
        val msg = messageRenderer.render(severity, message, location).trimEnd()
        with(replWriter) {
            when (severity) {
                CompilerMessageSeverity.EXCEPTION ->
                    logger.error("Internal exception occurred: $msg")
//                    sendInternalErrorReport(msg)
                CompilerMessageSeverity.ERROR -> {
                    logger.error(msg)
                }
                CompilerMessageSeverity.STRONG_WARNING -> {
                    logger.warn(msg)
//                    outputCompileError(msg)
                }
                // TODO consider reporting this and two below
                CompilerMessageSeverity.WARNING -> {
                    logger.warn(msg)
//                    outputCompileError(msg)
                }
                CompilerMessageSeverity.INFO -> {
                    logger.info(msg)
                }
                CompilerMessageSeverity.LOGGING -> {
                    logger.debug(msg)
                }
                else -> {
                    logger.debug(msg)
                }
            }
        }
    }

    override fun hasErrors(): Boolean = hasErrors
}
