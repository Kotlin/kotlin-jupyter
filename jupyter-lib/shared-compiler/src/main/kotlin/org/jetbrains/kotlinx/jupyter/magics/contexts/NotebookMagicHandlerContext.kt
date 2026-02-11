package org.jetbrains.kotlinx.jupyter.magics.contexts

import org.jetbrains.kotlinx.jupyter.api.Notebook

/**
 * Context interface for handlers that need to work with notebook operations.
 * Provides access to the notebook for executing code from external notebooks.
 */
class NotebookMagicHandlerContext(
    /**
     * The notebook instance for accessing working directory, execution host, and other notebook properties.
     */
    val notebook: Notebook,
) : MagicHandlerContext
