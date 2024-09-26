package org.jetbrains.kotlinx.jupyter.spring.starter

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.startup.createKernelPorts
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import java.io.File

@AutoConfiguration
@EnableConfigurationProperties(KotlinJupyterKernelServicePorts::class)
open class KotlinJupyterAutoConfiguration {
    @Bean
    open fun kernelService(servicePorts: KotlinJupyterKernelServicePorts): KotlinJupyterKernelService {
        val scriptClasspath =
            System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { File(it) }

        val ports =
            createKernelPorts { portType ->
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
        )
    }

    @Bean
    open fun springContext(): SpringContext {
        return SpringContext()
    }
}
