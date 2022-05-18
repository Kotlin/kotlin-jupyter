package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlinx.jupyter.api.AfterCellExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.ExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.codegen.ClassAnnotationsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.FieldsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.FileAnnotationsProcessor
import org.jetbrains.kotlinx.jupyter.codegen.ResultsRenderersProcessor
import org.jetbrains.kotlinx.jupyter.codegen.ThrowableRenderersProcessor
import org.jetbrains.kotlinx.jupyter.execution.InterruptionCallbacksProcessor
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesProcessor
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResourcesProcessor
import org.jetbrains.kotlinx.jupyter.magics.CompoundCodePreprocessor
import org.jetbrains.kotlinx.jupyter.repl.InternalEvaluator
import org.jetbrains.kotlinx.jupyter.repl.InternalVariablesMarkersProcessor

internal data class SharedReplContext(
    val classAnnotationsProcessor: ClassAnnotationsProcessor,
    val fileAnnotationsProcessor: FileAnnotationsProcessor,
    val fieldsProcessor: FieldsProcessor,
    val renderersProcessor: ResultsRenderersProcessor,
    val throwableRenderersProcessor: ThrowableRenderersProcessor,
    val codePreprocessor: CompoundCodePreprocessor,
    val resourcesProcessor: LibraryResourcesProcessor,
    val librariesProcessor: LibrariesProcessor,
    val librariesScanner: LibrariesScanner,
    val notebook: Notebook,
    val beforeCellExecution: MutableList<ExecutionCallback<*>>,
    val shutdownCodes: MutableList<ExecutionCallback<*>>,
    val evaluator: InternalEvaluator,
    val baseHost: BaseKernelHost,
    val internalVariablesMarkersProcessor: InternalVariablesMarkersProcessor,
    val interruptionCallbacksProcessor: InterruptionCallbacksProcessor,
) {
    val afterCellExecution = mutableListOf<AfterCellExecutionCallback>()
}
