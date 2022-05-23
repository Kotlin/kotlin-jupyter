package jupyter.kotlin.providers

import org.jetbrains.kotlinx.jupyter.api.Notebook

interface NotebookProvider {
    val notebook: Notebook
}
