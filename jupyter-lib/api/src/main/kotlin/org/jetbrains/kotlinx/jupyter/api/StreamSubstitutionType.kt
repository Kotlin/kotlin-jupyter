package org.jetbrains.kotlinx.jupyter.api

/**
 * Defines the way of how redirection from standard outputs works
 */
enum class StreamSubstitutionType {
    /**
     * On every cell execution, new capturing streams are created.
     * Everything that is written to / read from the standard streams
     * is redirected to these capturing streams. Redirection is locked
     * until the cell execution finishes, so multiple cells can't be executed
     * at the same time, even if they are executed on different REPLs within one process.
     */
    BLOCKING,

    /**
     * The same as for [BLOCKING], but subsequent cells don't block
     * on capturing the standard streams. Instead, standard streams are redirected
     * to these new cells as well. It may lead to the situation of mixing outputs from
     * multiple cells, but at least we don't lock on a single cell execution
     */
    NON_BLOCKING,
}
