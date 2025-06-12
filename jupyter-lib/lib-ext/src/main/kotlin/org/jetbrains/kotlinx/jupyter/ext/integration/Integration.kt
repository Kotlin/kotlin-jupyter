package org.jetbrains.kotlinx.jupyter.ext.integration

import org.jetbrains.kotlinx.jupyter.api.graphs.GraphNode
import org.jetbrains.kotlinx.jupyter.api.graphs.GraphNodes
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import org.jetbrains.kotlinx.jupyter.ext.graph.structure.Graph
import org.jetbrains.kotlinx.jupyter.ext.graph.structure.MultiGraph
import org.jetbrains.kotlinx.jupyter.ext.graph.visualization.render
import org.jetbrains.kotlinx.jupyter.ext.graph.wrappers.KClassNode
import java.io.File

class Integration : JupyterIntegration() {
    override fun Builder.onLoaded() {
        import("org.jetbrains.kotlinx.jupyter.ext.*")
        importPackage<Graph<*>>()
        importPackage<KClassNode>()

        import<File>()

        render<MultiGraph<*>> {
            it.render()
        }

        renderWithDefinedRenderers<GraphNode<*>> { Graph.of(it) }
        renderWithDefinedRenderers<GraphNodes<*>> { Graph.of(it.nodes) }
    }

    private inline fun <reified T : Any> Builder.renderWithDefinedRenderers(crossinline getValue: (T) -> Any?) {
        renderWithHost<T> { host, value ->
            notebook.renderersProcessor.renderValue(host, getValue(value)) ?: "null"
        }
    }
}
