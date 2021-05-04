package org.jetbrains.kotlinx.jupyter.ext.integration

import org.jetbrains.kotlinx.jupyter.api.annotations.JupyterLibrary
import org.jetbrains.kotlinx.jupyter.api.graphs.GraphNode
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import org.jetbrains.kotlinx.jupyter.ext.graph.structure.Graph
import org.jetbrains.kotlinx.jupyter.ext.graph.structure.MultiGraph
import org.jetbrains.kotlinx.jupyter.ext.graph.visualization.render
import org.jetbrains.kotlinx.jupyter.ext.graph.wrappers.KClassNode

@JupyterLibrary
class Integration : JupyterIntegration() {
    override fun Builder.onLoaded() {
        import("org.jetbrains.kotlinx.jupyter.ext.*")
        importPackage<GraphNode<*>>()
        importPackage<KClassNode>()

        render<MultiGraph<*>> {
            it.render()
        }
        renderWithHost<GraphNode<*>> { host, value ->
            notebook.renderersProcessor.renderValue(host, Graph.of(value)) ?: "null"
        }
    }
}
