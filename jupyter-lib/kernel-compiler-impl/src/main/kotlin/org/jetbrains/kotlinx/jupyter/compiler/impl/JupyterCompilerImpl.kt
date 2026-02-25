package org.jetbrains.kotlinx.jupyter.compiler.impl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.compiler.api.JupyterCompiler
import org.jetbrains.kotlinx.jupyter.config.JupyterCompilingOptions
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
import org.jetbrains.kotlinx.jupyter.config.jupyterOptions
import org.jetbrains.kotlinx.jupyter.config.toExecutionCount
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.removeDuplicates
import org.jetbrains.kotlinx.jupyter.repl.CellErrorMetaData
import org.jetbrains.kotlinx.jupyter.util.createCachedFun
import kotlin.script.experimental.api.ReplCompiler
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.compiler.util.SourceCodeImpl
import kotlin.script.experimental.api.repl
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.util.LinkedSnippet

open class JupyterCompilerImpl<CompilerT : ReplCompiler<KJvmCompiledScript>>(
    protected val compiler: CompilerT,
    protected val compilationConfig: ScriptCompilationConfiguration,
) : JupyterCompiler {
    override val version: KotlinKernelVersion = currentKernelVersion

    private val getCompilationConfiguration =
        createCachedFun { options: JupyterCompilingOptions ->
            compilationConfig.with {
                jupyterOptions(options)
                repl {
                    // This is also setting the $resX value
                    // It might be required for the new result value handling,
                    // if so, we need to track an always incrementing number
                    // on our end, which will be quite annoying.
                    // See KT-76172
                    // currentLineId(LineId(options.cellId.value, 0, 0))
                }
            }
        }

    override fun compileSync(
        snippetId: Int,
        code: String,
        options: JupyterCompilingOptions,
    ): LinkedSnippet<KJvmCompiledScript> {
        val snippet = SourceCodeImpl(snippetId, code)
        val compilationConfigWithJupyterOptions = getCompilationConfiguration(options)
        when (val resultWithDiagnostics = runBlocking { compiler.compile(snippet, compilationConfigWithJupyterOptions) }) {
            is ResultWithDiagnostics.Failure -> {
                val metadata =
                    CellErrorMetaData(
                        options.cellId.toExecutionCount(),
                        code.lines().size,
                    )
                // Work-around for KT-74685
                val updatedDiagnostics = resultWithDiagnostics.removeDuplicates()
                throw ReplCompilerException(code, updatedDiagnostics, metadata = metadata)
            }
            is ResultWithDiagnostics.Success -> {
                // TODO "resultField" is null because in K2 the return value is no longer stored
                //  in a variable. This is breaking FieldHandler integration. We need to find a way
                //  to reference previous cells outputs using code so we cal do something like `val x = notebook.outputs
                //  See KT-76172
                return resultWithDiagnostics.value
            }
        }
    }
}
