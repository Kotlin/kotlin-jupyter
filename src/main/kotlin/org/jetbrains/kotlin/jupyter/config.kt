package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.JavaRuntime
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
import org.jetbrains.kotlin.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlin.jupyter.config.defaultRepositories
import org.jetbrains.kotlin.jupyter.config.getLogger
import org.jetbrains.kotlin.jupyter.config.readResourceAsIniFile
import org.jetbrains.kotlin.jupyter.dependencies.ResolverConfig
import org.jetbrains.kotlin.jupyter.libraries.LibraryFactory
import org.zeromq.SocketType
import java.io.File

const val protocolVersion = "5.3"

internal val log by lazy { getLogger() }

val defaultRuntimeProperties by lazy {
    RuntimeKernelProperties(readResourceAsIniFile("runtime.properties"))
}

enum class JupyterSockets(val zmqKernelType: SocketType, val zmqClientType: SocketType) {
    hb(SocketType.REP, SocketType.REQ),
    shell(SocketType.ROUTER, SocketType.REQ),
    control(SocketType.ROUTER, SocketType.REQ),
    stdin(SocketType.ROUTER, SocketType.REQ),
    iopub(SocketType.PUB, SocketType.SUB)
}

data class OutputConfig(
    var captureOutput: Boolean = true,
    var captureBufferTimeLimitMs: Long = 100,
    var captureBufferMaxSize: Int = 1000,
    var cellOutputMaxSize: Int = 100000,
    var captureNewlineBufferSize: Int = 100
) {
    fun update(other: OutputConfig) {
        captureOutput = other.captureOutput
        captureBufferTimeLimitMs = other.captureBufferTimeLimitMs
        captureBufferMaxSize = other.captureBufferMaxSize
        cellOutputMaxSize = other.cellOutputMaxSize
        captureNewlineBufferSize = other.captureNewlineBufferSize
    }
}

class RuntimeKernelProperties(val map: Map<String, String>) : ReplRuntimeProperties {
    override val version: KotlinKernelVersion? by lazy {
        map["version"]?.let { KotlinKernelVersion.from(it) }
    }
    override val librariesFormatVersion: Int
        get() = map["librariesFormatVersion"]?.toIntOrNull() ?: throw RuntimeException("Libraries format version is not specified!")
    override val currentBranch: String
        get() = map["currentBranch"] ?: throw RuntimeException("Current branch is not specified!")
    override val currentSha: String
        get() = map["currentSha"] ?: throw RuntimeException("Current commit SHA is not specified!")
    override val jvmTargetForSnippets by lazy {
        map["jvmTargetForSnippets"] ?: JavaRuntime.version
    }
}

@Serializable(KernelJupyterParamsSerializer::class)
data class KernelJupyterParams(
    val sigScheme: String?,
    val key: String?,
    val ports: List<Int>,
    val transport: String?
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
            JupyterSockets.values().map { socket ->
                val fieldName = "${socket.name}_port"
                map[fieldName]?.let { Json.decodeFromJsonElement<Int>(it) } ?: throw RuntimeException("Cannot find $fieldName in config")
            },
            map["transport"]?.content ?: "tcp"
        )
    }

    override fun serialize(encoder: Encoder, value: KernelJupyterParams) {
        val map = mutableMapOf(
            "signature_scheme" to JsonPrimitive(value.sigScheme),
            "key" to JsonPrimitive(value.key),
            "transport" to JsonPrimitive(value.transport)
        )
        JupyterSockets.values().forEach {
            map["${it.name}_port"] = JsonPrimitive(value.ports[it.ordinal])
        }
        utilSerializer.serialize(encoder, map)
    }
}

data class KernelConfig(
    val ports: List<Int>,
    val transport: String,
    val signatureScheme: String,
    val signatureKey: String,
    val pollingIntervalMillis: Long = 100,
    val scriptClasspath: List<File> = emptyList(),
    val homeDir: File?,
    val resolverConfig: ResolverConfig?,
    val libraryFactory: LibraryFactory,
    val embedded: Boolean = false,
) {
    fun toArgs(prefix: String = ""): KernelArgs {
        val params = KernelJupyterParams(signatureScheme, signatureKey, ports, transport)

        val cfgFile = File.createTempFile("kotlin-kernel-config-$prefix", ".json")
        cfgFile.deleteOnExit()
        val format = Json { prettyPrint = true }
        cfgFile.writeText(format.encodeToString(params))

        return KernelArgs(cfgFile, scriptClasspath, homeDir)
    }

    companion object {
        fun fromArgs(args: KernelArgs, libraryFactory: LibraryFactory): KernelConfig {
            val (cfgFile, scriptClasspath, homeDir) = args
            val cfg = KernelJupyterParams.fromFile(cfgFile)
            return fromConfig(cfg, libraryFactory, scriptClasspath, homeDir)
        }

        fun fromConfig(cfg: KernelJupyterParams, libraryFactory: LibraryFactory, scriptClasspath: List<File>, homeDir: File?, embedded: Boolean = false): KernelConfig {

            return KernelConfig(
                ports = cfg.ports,
                transport = cfg.transport ?: "tcp",
                signatureScheme = cfg.sigScheme ?: "hmac1-sha256",
                signatureKey = if (cfg.sigScheme == null || cfg.key == null) "" else cfg.key,
                scriptClasspath = scriptClasspath,
                homeDir = homeDir,
                resolverConfig = homeDir?.let { loadResolverConfig(it.toString(), libraryFactory) },
                libraryFactory = libraryFactory,
                embedded = embedded,
            )
        }
    }
}

fun loadResolverConfig(homeDir: String, libraryFactory: LibraryFactory) = ResolverConfig(defaultRepositories, libraryFactory.getStandardResolver(homeDir))
