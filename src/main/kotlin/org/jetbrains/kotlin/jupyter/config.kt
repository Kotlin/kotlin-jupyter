package org.jetbrains.kotlin.jupyter

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import jupyter.kotlin.JavaRuntime
import khttp.responses.Response
import org.jetbrains.kotlin.jupyter.api.Code
import org.jetbrains.kotlin.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlin.jupyter.api.LibraryDefinition
import org.jetbrains.kotlin.jupyter.api.TypeHandler
import org.jetbrains.kotlin.jupyter.libraries.LibraryFactory
import org.jetbrains.kotlin.jupyter.libraries.LibraryResolver
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

val defaultRepositories = arrayOf(
    "https://jcenter.bintray.com/",
    "https://repo.maven.apache.org/maven2/",
    "https://jitpack.io/",
).map { RepositoryCoordinates(it) }

val defaultGlobalImports = listOf(
    "kotlin.math.*",
    "org.jetbrains.kotlin.jupyter.api.*",
)

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

data class KernelJupyterParams(
    val sigScheme: String?,
    val key: String?,
    val ports: List<Int>,
    val transport: String?
) {
    companion object {
        fun fromFile(cfgFile: File): KernelJupyterParams {
            val cfgJson = Parser.default().parse(cfgFile.canonicalPath) as JsonObject
            fun JsonObject.getInt(field: String): Int = int(field)
                ?: throw RuntimeException("Cannot find $field in $cfgFile")

            val sigScheme = cfgJson.string("signature_scheme")
            val key = cfgJson.string("key")
            val ports = JupyterSockets.values().map { cfgJson.getInt("${it.name}_port") }
            val transport = cfgJson.string("transport") ?: "tcp"
            return KernelJupyterParams(sigScheme, key, ports, transport)
        }
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
        val cfgJson = jsonObject(
            "transport" to transport,
            "signature_scheme" to signatureScheme,
            "key" to signatureKey,
        ).also { cfg ->
            JupyterSockets.values().forEach { cfg["${it.name}_port"] = ports[it.ordinal] }
        }

        val cfgFile = createTempFile("kotlin-kernel-config-$prefix", ".json")
        cfgFile.deleteOnExit()
        cfgFile.writeText(cfgJson.toJsonString(true))

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

data class Variable(val name: String, val value: String, val required: Boolean = false)

open class LibraryDefinition(
        val dependencies: List<String>,
        val initCell: List<String>,
        val imports: List<String>,
        val repositories: List<String>,
        val init: List<String>,
        val shutdown: List<String>,
        val renderers: List<TypeHandler>,
        val converters: List<TypeHandler>,
        val annotations: List<TypeHandler>
)

class LibraryDescriptor(
        val originalJson: JsonObject,
        dependencies: List<String>,
        val variables: List<Variable>,
        initCell: List<String>,
        imports: List<String>,
        repositories: List<String>,
        init: List<String>,
        shutdown: List<String>,
        renderers: List<TypeHandler>,
        converters: List<TypeHandler>,
        annotations: List<TypeHandler>,
        val link: String?,
        val description: String?,
        val minKernelVersion: String?,
) : LibraryDefinition(dependencies, initCell, imports, repositories, init, shutdown, renderers, converters, annotations)

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
