package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlinx.jupyter.api.AfterCellExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.codegen.ClassAnnotationsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.FieldsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.FileAnnotationsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.ResultsRenderersProcessor
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResourcesProcessor
import org.jetbrains.kotlinx.jupyter.magics.CompoundCodePreprocessor
import org.jetbrains.kotlinx.jupyter.repl.InternalEvaluator
import kotlin.reflect.KType

internal data class SharedReplContext(
    val classAnnotationsProcessor: ClassAnnotationsProcessor,
    val fileAnnotationsProcessor: FileAnnotationsProcessor,
    val fieldsProcessor: FieldsProcessor,
    val renderersProcessor: ResultsRenderersProcessor,
    val codePreprocessor: CompoundCodePreprocessor,
    val resourcesProcessor: LibraryResourcesProcessor,
    val librariesScanner: LibrariesScanner,
    val notebook: Notebook,
    val beforeCellExecution: MutableList<ExecutionCallback<*>>,
    val shutdownCodes: MutableList<ExecutionCallback<*>>,
    val implicitReceivers: MutableMap<KType, Any>,
    val evaluator: InternalEvaluator,
    val baseHost: BaseKernelHost,
) {
    val afterCellExecution = mutableListOf<AfterCellExecutionCallback>()
}
