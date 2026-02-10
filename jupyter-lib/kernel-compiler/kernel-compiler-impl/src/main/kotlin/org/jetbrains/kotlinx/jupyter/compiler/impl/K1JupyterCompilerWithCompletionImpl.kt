package org.jetbrains.kotlinx.jupyter.compiler.impl

import org.jetbrains.kotlin.scripting.ide_services.compiler.KJvmReplCompilerWithIdeServices
import kotlin.script.experimental.api.ScriptCompilationConfiguration

internal class K1JupyterCompilerWithCompletionImpl(
    compiler: KJvmReplCompilerWithIdeServices,
    compilationConfig: ScriptCompilationConfiguration,
) : JupyterCompilerImpl<KJvmReplCompilerWithIdeServices>(compiler, compilationConfig)
