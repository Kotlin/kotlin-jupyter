package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost

/**
 * Interface for handling magic commands.
 * Magic handlers process special commands prefixed with '%' in Jupyter notebooks.
 */
interface MagicsHandler {
    /**
     * Handles a magic command.
     *
     * @param magicText The text of the magic command (without the '%' prefix)
     * @param tryIgnoreErrors Whether to try to ignore errors
     * @param parseOnly Whether to only parse the command without executing it
     */
    fun handle(
        magicText: String,
        tryIgnoreErrors: Boolean,
        parseOnly: Boolean,
        host: KotlinKernelHost,
    )
}
