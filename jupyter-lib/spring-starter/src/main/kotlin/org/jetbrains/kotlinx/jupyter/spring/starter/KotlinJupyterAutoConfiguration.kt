package org.jetbrains.kotlinx.jupyter.spring.starter

import org.jetbrains.kotlinx.jupyter.ws.WsKernelPorts
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import java.io.File

/**
 * Autoconfiguration class for the Kotlin Jupyter kernel in a Spring Boot application.
 * This class sets up the necessary beans for starting Kotlin Jupyter Kernel inside the application.
 */
@AutoConfiguration
@EnableConfigurationProperties(
    SpringKotlinJupyterKernelPort::class,
    SpringKotlinJupyterClient::class,
)
open class KotlinJupyterAutoConfiguration {
    @Bean
    open fun kernelService(
        servicePort: SpringKotlinJupyterKernelPort,
        client: SpringKotlinJupyterClient,
    ): KotlinJupyterKernelService {
        val scriptClasspath =
            System
                .getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { File(it) }

        val port = servicePort.websocketPort

        return KotlinJupyterKernelService(
            kernelPorts = WsKernelPorts(port),
            scriptClasspath = scriptClasspath,
            homeDir = null,
            clientType = client.type,
        )
    }

    @Bean
    open fun springContext(): SpringContext = SpringContext()
}
