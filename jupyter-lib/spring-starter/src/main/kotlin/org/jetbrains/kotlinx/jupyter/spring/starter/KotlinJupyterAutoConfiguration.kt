package org.jetbrains.kotlinx.jupyter.spring.starter

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.startup.ZmqKernelPorts
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
    SpringKotlinJupyterKernelPorts::class,
    SpringKotlinJupyterClient::class,
)
open class KotlinJupyterAutoConfiguration {
    @Bean
    open fun kernelService(
        servicePorts: SpringKotlinJupyterKernelPorts,
        client: SpringKotlinJupyterClient,
    ): KotlinJupyterKernelService {
        val scriptClasspath =
            System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { File(it) }

        val ports =
            ZmqKernelPorts { portType ->
                when (portType) {
                    JupyterSocketType.HB -> servicePorts.hb
                    JupyterSocketType.SHELL -> servicePorts.shell
                    JupyterSocketType.CONTROL -> servicePorts.control
                    JupyterSocketType.STDIN -> servicePorts.stdin
                    JupyterSocketType.IOPUB -> servicePorts.iopub
                }
            }

        return KotlinJupyterKernelService(
            kernelPorts = ports,
            scriptClasspath = scriptClasspath,
            homeDir = null,
            clientType = client.type,
        )
    }

    @Bean
    open fun springContext(): SpringContext {
        return SpringContext()
    }
}
