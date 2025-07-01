package org.jetbrains.kotlinx.jupyter.startup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer
import org.jetbrains.kotlinx.jupyter.api.DEFAULT
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.jupyterName
import org.jetbrains.kotlinx.jupyter.protocol.HMAC
import org.jetbrains.kotlinx.jupyter.startup.parameters.KernelArgumentsBuilder
import org.jetbrains.kotlinx.jupyter.startup.parameters.KernelOwnParams
import java.io.File
import java.util.EnumMap

const val KERNEL_TRANSPORT_SCHEME = "tcp"
const val KERNEL_SIGNATURE_SCHEME = "HmacSHA256"

interface KernelPorts {
    /**
     * Returns JSON fields to be serialized into the config file (see [KernelJupyterParams]).
     * Needs to be symmetric with [JupyterServerRunner.tryDeserializePorts] implementation.
     */
    fun serialize(): Map<String, JsonPrimitive>
}

class ZmqKernelPorts(
    val ports: Map<JupyterSocketType, Int>,
) : KernelPorts {
    companion object {
        inline operator fun invoke(getSocketPort: (JupyterSocketType) -> Int) =
            ZmqKernelPorts(
                ports =
                    EnumMap<JupyterSocketType, Int>(JupyterSocketType::class.java).apply {
                        JupyterSocketType.entries.forEach { socket ->
                            val port = getSocketPort(socket)
                            put(socket, port)
                        }
                    },
            )

        fun tryDeserialize(jsonFields: Map<String, JsonPrimitive>): ZmqKernelPorts? {
            return ZmqKernelPorts { socket ->
                val fieldName = socket.zmqPortField
                jsonFields[fieldName]?.let { Json.decodeFromJsonElement<Int>(it) }
                    ?: return null
            }
        }

        private val JupyterSocketType.zmqPortField get() = "${jupyterName}_port"
    }

    override fun serialize(): Map<String, JsonPrimitive> =
        ports.entries.associate { (socket, port) ->
            socket.zmqPortField to JsonPrimitive(port)
        }
}

data class KernelArgs(
    val cfgFile: File,
    val ownParams: KernelOwnParams,
) {
    fun argsList(): List<String> = KernelArgumentsBuilder(this).argsList()
}

@Serializable(KernelJupyterParamsSerializer::class)
data class KernelJupyterParams(
    val signatureScheme: String,
    val signatureKey: String,
    val host: String,
    val ports: KernelPorts,
    val transport: String?,
) {
    val hmac by lazy {
        HMAC(
            algorithm = signatureScheme.replace("-", ""),
            key = signatureKey,
        )
    }

    companion object {
        fun fromFile(cfgFile: File): KernelJupyterParams {
            val jsonString = cfgFile.canonicalFile.readText()
            return Json.decodeFromString(jsonString)
        }
    }
}

object KernelJupyterParamsSerializer : KSerializer<KernelJupyterParams> {
    private val utilSerializer = serializer<Map<String, JsonPrimitive>>()

    override val descriptor: SerialDescriptor
        get() = utilSerializer.descriptor

    override fun deserialize(decoder: Decoder): KernelJupyterParams {
        val map = utilSerializer.deserialize(decoder)
        val ports =
            JupyterServerRunner.instances.firstNotNullOfOrNull {
                it.tryDeserializePorts(map)
            } ?: error("Unknown ports scheme")

        return KernelJupyterParams(
            signatureScheme = map["signature_scheme"]?.content ?: KERNEL_SIGNATURE_SCHEME,
            signatureKey = map["key"]?.content.orEmpty(),
            host = map["host"]?.content ?: ANY_HOST_NAME,
            ports = ports,
            transport = map["transport"]?.content ?: KERNEL_TRANSPORT_SCHEME,
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: KernelJupyterParams,
    ) {
        val map =
            mutableMapOf(
                "signature_scheme" to JsonPrimitive(value.signatureScheme),
                "key" to JsonPrimitive(value.signatureKey),
                "transport" to JsonPrimitive(value.transport),
                "host" to JsonPrimitive(value.host),
            )
        map.putAll(value.ports.serialize())
        utilSerializer.serialize(encoder, map)
    }
}

const val ANY_HOST_NAME = "*"

data class KernelConfig(
    val jupyterParams: KernelJupyterParams,
    val ownParams: KernelOwnParams,
) {
    fun toArgs(prefix: String = ""): KernelArgs {
        val cfgFile = File.createTempFile("kotlin-kernel-config-$prefix", ".json")
        cfgFile.deleteOnExit()
        val format = Json { prettyPrint = true }
        cfgFile.writeText(format.encodeToString(jupyterParams))

        return KernelArgs(
            cfgFile = cfgFile,
            ownParams = ownParams,
        )
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
        KernelOwnParams(
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
        KernelOwnParams(
            scriptClasspath = scriptClasspath,
            homeDir = homeDir,
            debugPort = debugPort,
            clientType = clientType,
            replCompilerMode = replCompilerMode,
        ),
)

const val MAIN_CLASS_NAME = "org.jetbrains.kotlinx.jupyter.IkotlinKt"

fun KernelConfig.javaCmdLine(
    // Path to java executable or just "java" in case it's on the path
    javaExecutable: String,
    // Prefix for the temporary directory where the connection file should be stored
    tempDirPrefix: String,
    // Classpath for the whole kernel. Should include kernel artifact
    kernelClasspath: String,
    // Any JVM arguments such as -XmX
    extraJavaArguments: Collection<String> = emptyList(),
): List<String> {
    val args = toArgs(tempDirPrefix).argsList().toTypedArray()
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

fun KernelArgs.getConfig(): KernelConfig =
    KernelConfig(
        jupyterParams = KernelJupyterParams.fromFile(cfgFile),
        ownParams = ownParams,
    )
