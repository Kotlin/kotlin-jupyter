package org.jetbrains.kotlinx.jupyter.config

/**
 * Represents a cell ID in the notebook.
 * This is separate from ExecutionCount which is 1-based, while CellId is 0-based.
 */
@JvmInline
value class CellId(
    val value: Int,
) {
    override fun toString(): String = value.toString()

    companion object {
        // Marker CellId for code executed outside the context of a cell.
        val NO_CELL: CellId = CellId(-1)
    }
}

/**
 * Options that control how a snippet is compiled.
 */
data class JupyterCompilingOptions(
    val cellId: CellId,
    val isUserCode: Boolean,
) {
    companion object {
        val DEFAULT = JupyterCompilingOptions(CellId.NO_CELL, false)
    }
}
