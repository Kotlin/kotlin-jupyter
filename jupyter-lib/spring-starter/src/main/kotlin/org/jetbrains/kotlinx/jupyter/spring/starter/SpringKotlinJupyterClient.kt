package org.jetbrains.kotlinx.jupyter.spring.starter

import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "kotlin.jupyter.kernel.client")
class SpringKotlinJupyterClient {
    var type: String = JupyterClientType.KOTLIN_NOTEBOOK.name
}
