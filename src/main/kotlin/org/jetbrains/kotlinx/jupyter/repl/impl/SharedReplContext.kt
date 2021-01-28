package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.AfterCellExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.libraries.Execution
import org.jetbrains.kotlinx.jupyter.codegen.ClassAnnotationsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.FieldsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.FileAnnotationsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.TypeRenderersProcessor
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResourcesProcessor
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor

internal data class SharedReplContext(
    val classAnnotationsProcessor: ClassAnnotationsProcessor,
    val fileAnnotationsProcessor: FileAnnotationsProcessor,
    val fieldsProcessor: FieldsProcessor,
    val typeRenderersProcessor: TypeRenderersProcessor,
    val magicsProcessor: MagicsProcessor,
    val resourcesProcessor: LibraryResourcesProcessor,
    val librariesScanner: LibrariesScanner,
    val notebook: Notebook<*>,
    val beforeCellExecution: MutableList<Execution<*>>,
    val shutdownCodes: MutableList<Execution<*>>,
    val evaluator: InternalEvaluator,
    val baseHost: BaseKernelHost
) {
    val afterCellExecution = mutableListOf<AfterCellExecutionCallback>()
}
