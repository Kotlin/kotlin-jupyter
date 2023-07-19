package org.jetbrains.kotlinx.jupyter.startup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
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
import java.util.ArrayList
import java.util.EnumMap

typealias KernelPorts = Map<JupyterSocketType, Int>

const val kernelTransportProtocol = "tcp"
const val kernelSignatureScheme = "hmac1-sha256"

fun createKernelPorts(action: (JupyterSocketType) -> Int): KernelPorts {
    return EnumMap<JupyterSocketType, Int>(JupyterSocketType::class.java).apply {
        JupyterSocketType.values().forEach { socket ->
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
            map["transport"]?.content ?: kernelTransportProtocol,
        )
    }

    override fun serialize(encoder: Encoder, value: KernelJupyterParams) {
        val map = mutableMapOf(
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

fun createKotlinKernelConfig(
    // Mapping of Jupyter sockets to the corresponding ports. Server is responsible for opening ports
    ports: KernelPorts,

    // Signature key that should be used for signing messages. See:
    // https://jupyter-client.readthedocs.io/en/stable/messaging.html#wire-protocol
    // https://jupyter-client.readthedocs.io/en/stable/kernels.html#connection-files
    signatureKey: String,

    // All JARs that should be in initial script (not whole kernel) classpath
    scriptClasspath: List<File> = emptyList(),

    // Home directory. In sub-folders of this directory libraries descriptors and their caches are stored
    homeDir: File? = null,

    // If not null, kernel should listen to the debugger on this port
    debugPort: Int? = null,
) = KernelConfig(
    ports,
    kernelTransportProtocol,
    kernelSignatureScheme,
    signatureKey,
    scriptClasspath,
    homeDir,
    debugPort,
)

const val mainClassName = "org.jetbrains.kotlinx.jupyter.IkotlinKt"

fun KernelConfig.javaCmdLine(
    // Path to java executable or just "java" in case it's on path
    javaExecutable: String,

    // Prefix for temporary directory where connection file should be stored
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
        add(mainClassName)
        addAll(args)
    }
}

fun KernelArgs.getConfig(): KernelConfig {
    val cfg = parseParams()

    return KernelConfig(
        ports = cfg.ports,
        transport = cfg.transport ?: kernelTransportProtocol,
        signatureScheme = cfg.signatureScheme ?: kernelSignatureScheme,
        signatureKey = if (cfg.signatureScheme == null || cfg.key == null) "" else cfg.key,
        scriptClasspath = scriptClasspath,
        homeDir = homeDir,
        debugPort = debugPort,
        clientType = clientType,
        jvmTargetForSnippets = jvmTargetForSnippets,
    )
}
