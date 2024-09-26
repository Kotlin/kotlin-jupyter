package org.jetbrains.kotlinx.jupyter.spring.starter

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.startup.defaultSpringAppPorts
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "kotlin.jupyter.kernel.ports")
class KotlinJupyterKernelServicePorts {
    var hb: Int = getDefaultPort(JupyterSocketType.HB)
    var shell: Int = getDefaultPort(JupyterSocketType.SHELL)
    var control: Int = getDefaultPort(JupyterSocketType.CONTROL)
    var stdin: Int = getDefaultPort(JupyterSocketType.STDIN)
    var iopub: Int = getDefaultPort(JupyterSocketType.IOPUB)
}

private fun getDefaultPort(type: JupyterSocketType): Int {
    return defaultSpringAppPorts[type] ?: error("Default for $type is not known")
}
