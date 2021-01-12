package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.libraries.Execution
import org.jetbrains.kotlinx.jupyter.codegen.AnnotationsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.TypeProvidersProcessor
import org.jetbrains.kotlinx.jupyter.codegen.TypeRenderersProcessor
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResourcesProcessor
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor

internal data class SharedReplContext(
    val annotationsProcessor: AnnotationsProcessor,
    val typeProvidersProcessor: TypeProvidersProcessor,
    val typeRenderersProcessor: TypeRenderersProcessor,
    val magicsProcessor: MagicsProcessor,
    val resourcesProcessor: LibraryResourcesProcessor,
    val librariesScanner: LibrariesScanner,
    val notebook: Notebook<*>,
    val initCellCodes: MutableList<Execution<*>>,
    val shutdownCodes: MutableList<Execution<*>>,
    val evaluator: InternalEvaluator,
    val baseHost: BaseKernelHost
)
