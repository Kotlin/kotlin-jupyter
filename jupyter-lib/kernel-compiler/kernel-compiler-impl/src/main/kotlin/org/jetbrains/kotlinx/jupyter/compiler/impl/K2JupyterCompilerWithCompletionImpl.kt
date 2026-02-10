package org.jetbrains.kotlinx.jupyter.compiler.impl

import kotlin.script.experimental.api.ScriptCompilationConfiguration

/**
 * Currently just a copy of [K1JupyterCompilerWithCompletionImpl] with the only difference being the type-parameter
 * of [compiler]. The reason is that we cannot create a unified type-abstraction for both of them right now, due to
 * [K2KJvmReplCompilerWithCompletion] being located in the Kotlin Kernel and [org.jetbrains.kotlin.scripting.ide_services.compiler.KJvmReplCompilerWithIdeServices]
 * being inside the compiler
 */
internal class K2JupyterCompilerWithCompletionImpl(
    compiler: K2KJvmReplCompilerWithCompletion,
    compilationConfig: ScriptCompilationConfiguration,
) : JupyterCompilerImpl<K2KJvmReplCompilerWithCompletion>(compiler, compilationConfig) {
    override fun close() {
        compiler.close()
    }
}
