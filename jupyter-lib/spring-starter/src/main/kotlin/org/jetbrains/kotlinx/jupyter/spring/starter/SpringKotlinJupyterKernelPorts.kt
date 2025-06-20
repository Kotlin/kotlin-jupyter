package org.jetbrains.kotlinx.jupyter.spring.starter

import org.jetbrains.kotlinx.jupyter.startup.defaultSpringAppWebsocketPort
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "kotlin.jupyter.kernel.ports")
class SpringKotlinJupyterKernelPort {
    var websocketPort: Int = defaultSpringAppWebsocketPort
}
