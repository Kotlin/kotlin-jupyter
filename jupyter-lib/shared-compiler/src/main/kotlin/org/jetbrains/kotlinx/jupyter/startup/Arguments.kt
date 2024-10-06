package org.jetbrains.kotlinx.jupyter.startup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.portField
import org.jetbrains.kotlinx.jupyter.protocol.HMAC
import java.io.File
import java.util.EnumMap

typealias KernelPorts = Map<JupyterSocketType, Int>

const val KERNEL_TRANSPORT_SCHEME = "tcp"
const val KERNEL_SIGNATURE_SCHEME = "HmacSHA256"

fun createKernelPorts(action: (JupyterSocketType) -> Int): KernelPorts {
    return EnumMap<JupyterSocketType, Int>(JupyterSocketType::class.java).apply {
        JupyterSocketType.entries.forEach { socket ->
            val port = action(socket)
            put(socket, port)
        }
    }
}

data class KernelArgs(
    val cfgFile: File,
    val scriptClasspath: List<File>,
    val homeDir: File?,
    val debugPort: Int?,
    val clientType: String?,
    val jvmTargetForSnippets: String?,
) {
    fun parseParams(): KernelJupyterParams {
        return KernelJupyterParams.fromFile(cfgFile)
    }

    fun argsList(): List<String> {
        return mutableListOf<String>().apply {
            add(cfgFile.absolutePath)
            homeDir?.let { add("-home=${it.absolutePath}") }
            if (scriptClasspath.isNotEmpty()) {
                val classPathString = scriptClasspath.joinToString(File.pathSeparator) { it.absolutePath }
                add("-cp=$classPathString")
            }
            debugPort?.let { add("-debugPort=$it") }
            clientType?.let { add("-client=$it") }
            jvmTargetForSnippets?.let { add("-jvmTarget=$it") }
        }
    }
}

@Serializable(KernelJupyterParamsSerializer::class)
data class KernelJupyterParams(
    val signatureScheme: String?,
    val key: String?,
    val ports: KernelPorts,
    val transport: String?,
) {
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

        return KernelJupyterParams(
            map["signature_scheme"]?.content,
            map["key"]?.content,
            createKernelPorts { socket ->
                val fieldName = socket.portField
                map[fieldName]?.let { Json.decodeFromJsonElement<Int>(it) } ?: throw RuntimeException("Cannot find $fieldName in config")
            },
            map["transport"]?.content ?: KERNEL_TRANSPORT_SCHEME,
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: KernelJupyterParams,
    ) {
        val map =
            mutableMapOf(
                "signature_scheme" to JsonPrimitive(value.signatureScheme),
                "key" to JsonPrimitive(value.key),
                "transport" to JsonPrimitive(value.transport),
            )
        value.ports.forEach { (socket, port) ->
            map[socket.portField] = JsonPrimitive(port)
        }
        utilSerializer.serialize(encoder, map)
    }
}

data class KernelConfig(
    val host: String = "*",
    val ports: KernelPorts,
    val transport: String,
    val signatureScheme: String,
    val signatureKey: String,
    val scriptClasspath: List<File> = emptyList(),
    val homeDir: File?,
    val debugPort: Int? = null,
    val clientType: String? = null,
    val jvmTargetForSnippets: String? = null,
) {
    val hmac by lazy {
        HMAC(signatureScheme.replace("-", ""), signatureKey)
    }

    fun toArgs(prefix: String = ""): KernelArgs {
        val params = KernelJupyterParams(signatureScheme, signatureKey, ports, transport)

        val cfgFile = File.createTempFile("kotlin-kernel-config-$prefix", ".json")
        cfgFile.deleteOnExit()
        val format = Json { prettyPrint = true }
        cfgFile.writeText(format.encodeToString(params))

        return KernelArgs(cfgFile, scriptClasspath, homeDir, debugPort, clientType, jvmTargetForSnippets)
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
) = KernelConfig(
    host = host,
    ports = ports,
    transport = KERNEL_TRANSPORT_SCHEME,
    signatureScheme = KERNEL_SIGNATURE_SCHEME,
    signatureKey = signatureKey,
    homeDir = null,
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
) = KernelConfig(
    ports = ports,
    transport = KERNEL_TRANSPORT_SCHEME,
    signatureScheme = KERNEL_SIGNATURE_SCHEME,
    signatureKey = signatureKey,
    scriptClasspath = scriptClasspath,
    homeDir = homeDir,
    debugPort = debugPort,
    clientType = clientType,
)

const val MAIN_CLASS_NAME = "org.jetbrains.kotlinx.jupyter.IkotlinKt"

fun KernelConfig.javaCmdLine(
    // Path to java executable or just "java" in case it's on the path
    javaExecutable: String,
    // Prefix for temporary directory where the connection file should be stored
    tempDirPrefix: String,
    // Classpath for the whole kernel. Should include kernel artifact
    kernelClasspath: String,
    // Any JVM arguments such as -XmX
    extraJavaArguments: Collection<String> = emptyList(),
): List<String> {
    val args = toArgs(tempDirPrefix).argsList().toTypedArray()

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

fun KernelArgs.getConfig(): KernelConfig {
    val cfg = parseParams()

    return KernelConfig(
        ports = cfg.ports,
        transport = cfg.transport ?: KERNEL_TRANSPORT_SCHEME,
        signatureScheme = cfg.signatureScheme ?: KERNEL_SIGNATURE_SCHEME,
        signatureKey = if (cfg.signatureScheme == null || cfg.key == null) "" else cfg.key,
        scriptClasspath = scriptClasspath,
        homeDir = homeDir,
        debugPort = debugPort,
        clientType = clientType,
        jvmTargetForSnippets = jvmTargetForSnippets,
    )
}
