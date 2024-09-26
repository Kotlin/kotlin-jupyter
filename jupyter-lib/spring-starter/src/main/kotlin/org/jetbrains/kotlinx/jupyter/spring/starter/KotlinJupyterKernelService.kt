package org.jetbrains.kotlinx.jupyter.spring.starter

import org.jetbrains.kotlinx.jupyter.api.EmbeddedKernelRunMode
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.libraries.DefaultResolutionInfoProviderFactory
import org.jetbrains.kotlinx.jupyter.startKernel
import org.jetbrains.kotlinx.jupyter.startup.DEFAULT_SPRING_SIGNATURE_KEY
import org.jetbrains.kotlinx.jupyter.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.startup.createKotlinKernelConfig
import java.io.Closeable
import java.io.File
import kotlin.concurrent.thread

class KotlinJupyterKernelService(
    kernelPorts: KernelPorts,
    scriptClasspath: List<File> = emptyList(),
    homeDir: File? = null,
) : Closeable {
    private val kernelConfig =
        createKotlinKernelConfig(
            kernelPorts,
            DEFAULT_SPRING_SIGNATURE_KEY,
            scriptClasspath,
            homeDir,
        )

    private val kernelThread =
        thread {
            startKernel(
                DefaultKernelLoggerFactory,
                EmbeddedKernelRunMode,
                kernelConfig,
                DefaultResolutionInfoProviderFactory,
            )
        }

    override fun close() {
        kernelThread.interrupt()
    }
}
