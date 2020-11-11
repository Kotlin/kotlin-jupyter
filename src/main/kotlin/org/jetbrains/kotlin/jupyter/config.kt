package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.JavaRuntime
import khttp.responses.Response
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
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
import org.jetbrains.kotlin.jupyter.api.Code
import org.jetbrains.kotlin.jupyter.api.CodeExecution
import org.jetbrains.kotlin.jupyter.api.ExactRendererTypeHandler
import org.jetbrains.kotlin.jupyter.api.GenerativeTypeHandler
import org.jetbrains.kotlin.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlin.jupyter.api.LibraryDefinition
import org.jetbrains.kotlin.jupyter.config.defaultRepositories
import org.jetbrains.kotlin.jupyter.libraries.LibraryFactory
import org.jetbrains.kotlin.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlin.jupyter.util.GenerativeHandlersSerializer
import org.jetbrains.kotlin.jupyter.util.ListToMapSerializer
import org.jetbrains.kotlin.jupyter.util.RenderersSerializer
import org.slf4j.LoggerFactory
import org.zeromq.SocketType
import java.io.File
import java.nio.file.Paths
import kotlin.script.experimental.dependencies.RepositoryCoordinates

const val LibrariesDir = "libraries"
const val LocalCacheDir = "cache"

val LocalSettingsPath = Paths.get(System.getProperty("user.home"), ".jupyter_kotlin").toString()

const val GitHubApiHost = "api.github.com"
const val GitHubRepoOwner = "kotlin"
const val GitHubRepoName = "kotlin-jupyter"
const val GitHubApiPrefix = "https://$GitHubApiHost/repos/$GitHubRepoOwner/$GitHubRepoName"

const val LibraryDescriptorExt = "json"
const val LibraryPropertiesFile = ".properties"

const val protocolVersion = "5.3"

internal val log by lazy { LoggerFactory.getLogger("ikotlin") }

val defaultRuntimeProperties by lazy {
    RuntimeKernelProperties(ClassLoader.getSystemResource("runtime.properties")?.readText()?.parseIniConfig().orEmpty())
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

        val cfgFile = createTempFile("kotlin-kernel-config-$prefix", ".json")
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

@Serializable
data class Variable(val name: String, val value: String, val required: Boolean = false)

object VariablesSerializer : ListToMapSerializer<Variable, String, String>(
    serializer(),
    ::Variable,
    { it.name to it.value }
)

@Serializable
class LibraryDescriptor(
    val libraryDefinitions: List<Code> = emptyList(),
    override val dependencies: List<String> = emptyList(),

    @Serializable(VariablesSerializer::class)
    @SerialName("properties")
    val variables: List<Variable> = emptyList(),

    override val initCell: List<CodeExecution> = emptyList(),
    override val imports: List<String> = emptyList(),
    override val repositories: List<String> = emptyList(),
    override val init: List<CodeExecution> = emptyList(),
    override val shutdown: List<CodeExecution> = emptyList(),

    @Serializable(RenderersSerializer::class)
    override val renderers: List<ExactRendererTypeHandler> = emptyList(),

    @Serializable(GenerativeHandlersSerializer::class)
    @SerialName("typeConverters")
    override val converters: List<GenerativeTypeHandler> = emptyList(),

    @Serializable(GenerativeHandlersSerializer::class)
    @SerialName("annotationHandlers")
    override val annotations: List<GenerativeTypeHandler> = emptyList(),

    val link: String? = null,
    val description: String? = null,
    val minKernelVersion: String? = null,
) : LibraryDefinition

data class ResolverConfig(
    val repositories: List<RepositoryCoordinates>,
    val libraries: LibraryResolver
)

fun getHttp(url: String): Response {
    val response = khttp.get(url)
    if (response.statusCode != 200)
        throw Exception("Http request failed. Url = $url. Response = $response")
    return response
}

fun loadResolverConfig(homeDir: String, libraryFactory: LibraryFactory) = ResolverConfig(defaultRepositories, libraryFactory.getStandardResolver(homeDir))
