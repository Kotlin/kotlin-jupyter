package org.jetbrains.kotlinx.jupyter.api.annotations

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import java.io.File

class JupyterSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val genPathStr = environment.options[KOTLIN_JUPYTER_GENERATED_PATH] ?: throw Exception("No path for generated files specified")

        return JupyterSymbolProcessor(environment.logger, File(genPathStr))
    }

    companion object {
        const val KOTLIN_JUPYTER_GENERATED_PATH = "kotlin.jupyter.fqn.path"
    }
}
