package org.jetbrains.kotlinx.jupyter.startup

import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.api.DEFAULT
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.protocol.startup.ANY_HOST_NAME
import org.jetbrains.kotlinx.jupyter.protocol.startup.AbstractKernelJupyterParamsSerializer
import org.jetbrains.kotlinx.jupyter.protocol.startup.KERNEL_SIGNATURE_SCHEME
import org.jetbrains.kotlinx.jupyter.protocol.startup.KERNEL_TRANSPORT_SCHEME
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelConfig
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.toArgs
import org.jetbrains.kotlinx.jupyter.startup.parameters.KotlinKernelOwnParams
import java.io.File

object KernelJupyterParamsSerializer : AbstractKernelJupyterParamsSerializer() {
    override fun deserializePorts(map: JsonObject): KernelPorts =
        JupyterServerRunner.instances.firstNotNullOfOrNull {
            it.tryDeserializePorts(map)
        } ?: error("Unknown ports scheme")
}

const val MAIN_CLASS_NAME = "org.jetbrains.kotlinx.jupyter.IkotlinKt"

fun KernelConfig<KotlinKernelOwnParams>.javaCmdLine(
    // Path to java executable or just "java" in case it's on the path
    javaExecutable: String,
    // Prefix for the temporary directory where the connection file should be stored
    tempDirPrefix: String,
    // Classpath for the whole kernel. Should include kernel artifact
    kernelClasspath: String,
    // Any JVM arguments such as -XmX
    extraJavaArguments: Collection<String> = emptyList(),
): List<String> {
    val args =
        toArgs(
            kernelJupyterParamsSerializer = KernelJupyterParamsSerializer,
            configFileSuffix = tempDirPrefix,
        ).argsList().toTypedArray()
    val debugPort = ownParams.debugPort

    return ArrayList<String>().apply {
        add(javaExecutable)
        addAll(extraJavaArguments)
        if (debugPort != null) {
            add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$debugPort")
        }
        add("-cp")
        add(kernelClasspath)
        add(MAIN_CLASS_NAME)
        addAll(args)
    }
}

/**
 * Creates a configuration for a Kotlin Kernel client.
 *
 * @param host the host to attach to
 * @param ports the ports used by the kernel
 * @param signatureKey the key used for signature
 */
@Suppress("unused") // Used in Kotlin Notebook in attached mode configuration
fun createClientKotlinKernelConfig(
    host: String,
    ports: KernelPorts,
    signatureKey: String,
    replCompilerMode: ReplCompilerMode,
    extraCompilerArgs: List<String>,
) = KernelConfig(
    jupyterParams =
        KernelJupyterParams(
            signatureScheme = KERNEL_SIGNATURE_SCHEME,
            signatureKey = signatureKey,
            host = host,
            ports = ports,
            transport = KERNEL_TRANSPORT_SCHEME,
        ),
    ownParams =
        KotlinKernelOwnParams(
            homeDir = null,
            replCompilerMode = replCompilerMode,
            extraCompilerArguments = extraCompilerArgs,
        ),
)

/**
 * Creates a configuration for the Kotlin Jupyter kernel.
 *
 * @param ports The mapping of Jupyter sockets to the corresponding ports, with the server opening the ports.
 * @param signatureKey The signature key used for signing messages, adhering to Jupyter's wire protocol.
 *  See:
 *      https://jupyter-client.readthedocs.io/en/stable/messaging.html#wire-protocol
 *      https://jupyter-client.readthedocs.io/en/stable/kernels.html#connection-files
 * @param scriptClasspath The list of JARs to be included in the initial script classpath.
 * @param homeDir The home directory where libraries, descriptors, and their caches are stored.
 * @param debugPort The port the kernel should listen on for the debugger, if not null.
 * @param clientType The type of client that will connect to the kernel, if specified.
 * @return KernelConfig instance populated with the provided parameters and default kernel configuration values.
 */
fun createKotlinKernelConfig(
    ports: KernelPorts,
    signatureKey: String,
    scriptClasspath: List<File> = emptyList(),
    homeDir: File? = null,
    debugPort: Int? = null,
    clientType: String? = null,
    replCompilerMode: ReplCompilerMode = ReplCompilerMode.DEFAULT,
) = KernelConfig(
    jupyterParams =
        KernelJupyterParams(
            signatureScheme = KERNEL_SIGNATURE_SCHEME,
            signatureKey = signatureKey,
            host = ANY_HOST_NAME,
            ports = ports,
            transport = KERNEL_TRANSPORT_SCHEME,
        ),
    ownParams =
        KotlinKernelOwnParams(
            scriptClasspath = scriptClasspath,
            homeDir = homeDir,
            debugPort = debugPort,
            clientType = clientType,
            replCompilerMode = replCompilerMode,
        ),
)
