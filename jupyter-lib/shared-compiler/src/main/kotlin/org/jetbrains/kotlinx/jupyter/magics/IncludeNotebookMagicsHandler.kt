package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.jupyter.parser.JupyterParser
import org.jetbrains.jupyter.parser.notebook.CodeCell
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException
import org.jetbrains.kotlinx.jupyter.magics.contexts.CommandHandlingMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.MagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.NotebookMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.requireContext
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Handler for the %includeNotebook magic command.
 * This command includes and executes all cells from another notebook as hidden code.
 */
class IncludeNotebookMagicsHandler(
    context: MagicHandlerContext,
) : BasicMagicsHandler(context) {
    private val notebookContext = context.requireContext<NotebookMagicHandlerContext>()

    override val callbackMap: Map<ReplLineMagic, () -> Unit> =
        mapOf(
            ReplLineMagic.INCLUDE_NOTEBOOK to ::handleIncludeNotebook,
        )

    /**
     * Handles the %includeNotebook command, which includes and executes cells from another notebook.
     */
    private fun handleIncludeNotebook() {

        val pathArg =
            commandHandlingContext.arg
                ?: throw ReplPreprocessingException("Path argument is required for 'includeNotebook' command")

        try {
            // Resolve the path relative to the notebook's working directory
            val notebookPath = resolvePath(pathArg.trim())

            if (!notebookPath.exists()) {
                throw ReplPreprocessingException("Notebook file not found: $notebookPath")
            }

            // Parse the notebook file
            val notebook = JupyterParser.parse(notebookPath.toFile())

            // Extract code cells
            val codeCells = notebook.cells.filterIsInstance<CodeCell>()

            // Get the execution host from the notebook
            val executionHost = commandHandlingContext.host ?: throw ReplPreprocessingException(
                "Execution host is not available. Cannot execute notebook cells.",
            )

            // Execute each code cell
            for (cell in codeCells) {
                val code = cell.source
                if (code.isNotBlank()) {
                    // Execute the code using the kernel host
                    executionHost.execute(code)
                }
            }
        } catch (e: ReplPreprocessingException) {
            throw e
        } catch (e: Exception) {
            if (!commandHandlingContext.tryIgnoreErrors) {
                throw ReplPreprocessingException(
                    "Failed to include notebook '$pathArg': ${e.message}",
                    e,
                )
            }
        }
    }

    /**
     * Resolves a path relative to the notebook's working directory.
     * If the path is already absolute, returns it as-is.
     */
    private fun resolvePath(pathString: String): Path {
        val path = File(pathString).toPath()
        return if (path.isAbsolute) {
            path
        } else {
            notebookContext.notebook.workingDir.resolve(path)
        }
    }

    companion object : MagicHandlerFactoryImpl(
        ::IncludeNotebookMagicsHandler,
        listOf(
            NotebookMagicHandlerContext::class,
            CommandHandlingMagicHandlerContext::class,
        ),
    )
}
