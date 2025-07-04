package org.jetbrains.kotlinx.jupyter.spring.starter

import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.createReplSettings
import org.jetbrains.kotlinx.jupyter.libraries.DefaultResolutionInfoProviderFactory
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.runServer
import org.jetbrains.kotlinx.jupyter.startup.DEFAULT_SPRING_SIGNATURE_KEY
import org.jetbrains.kotlinx.jupyter.startup.createKotlinKernelConfig
import java.io.Closeable
import java.io.File
import kotlin.concurrent.thread

/**
 * A service class responsible for managing and running a kernel server.
 * The server starts in a dedicated thread once the class is instantiated.
 * To interrupt server, call [close].
 *
 * This class has no Spring specifics but is placed here because it's unnecessary anywhere else
 *
 * @param kernelPorts The mapping of Jupyter socket types to the corresponding port numbers.
 * @param scriptClasspath A list of files that represent the initial classpath for REPL snippets.
 * @param homeDir The directory where libraries descriptors and caches are stored. It can be null.
 * @param clientType Client type name, see [JupyterClientType]
 */
class KotlinJupyterKernelService(
    kernelPorts: KernelPorts,
    scriptClasspath: List<File> = emptyList(),
    homeDir: File? = null,
    clientType: String? = null,
) : Closeable {
    private val kernelConfig =
        createKotlinKernelConfig(
            ports = kernelPorts,
            signatureKey = DEFAULT_SPRING_SIGNATURE_KEY,
            scriptClasspath = scriptClasspath,
            homeDir = homeDir,
            clientType = clientType,
        )

    private var shouldRestart = true

    private val kernelThread =
        thread(name = "KotlinJupyterKernelService.kernel") {
            runKernelService()
        }

    private fun runKernelService() {
        val replSettings =
            createReplSettings(
                DefaultKernelLoggerFactory,
                SpringProcessKernelRunMode,
                kernelConfig,
                DefaultResolutionInfoProviderFactory,
            )
        while (shouldRestart) {
            try {
                runServer(replSettings)
            } catch (_: InterruptedException) {
            }
        }
    }

    override fun close() {
        shouldRestart = false
        kernelThread.interrupt()
    }
}
