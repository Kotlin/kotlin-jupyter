package org.jetbrains.kotlin.jupyter

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import khttp.responses.Response
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.apache.commons.io.FileUtils
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.script.experimental.dependencies.RepositoryCoordinates

val LibrariesDir = "libraries"
val LocalCacheDir = "cache"
val CachedLibrariesFootprintFile = "libsCommit"

val LocalSettingsPath = Paths.get(System.getProperty("user.home"), ".jupyter_kotlin").toString()

val GitHubApiHost = "api.github.com"
val GitHubRepoOwner = "kotlin"
val GitHubRepoName = "kotlin-jupyter"
val GitHubBranchName = "master"
val GitHubApiPrefix = "https://$GitHubApiHost/repos/$GitHubRepoOwner/$GitHubRepoName"

val LibraryDescriptorExt = "json"
val LibraryPropertiesFile = ".properties"
val libraryDescriptorFormatVersion = 2

internal val log by lazy { LoggerFactory.getLogger("ikotlin") }

enum class JupyterSockets {
    hb,
    shell,
    control,
    stdin,
    iopub
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

data class RuntimeKernelProperties(val map: Map<String, String>) {
    val version: String
        get() = map["version"] ?: "unspecified"
}

val runtimeProperties by lazy {
    RuntimeKernelProperties(ClassLoader.getSystemResource("runtime.properties")?.readText()?.parseIniConfig().orEmpty())
}

data class KernelConfig(
        val ports: Array<Int>,
        val transport: String,
        val signatureScheme: String,
        val signatureKey: String,
        val pollingIntervalMillis: Long = 100,
        val scriptClasspath: List<File> = emptyList(),
        val resolverConfig: ResolverConfig?
)

val protocolVersion = "5.3"

data class TypeHandler(val className: TypeName, val code: Code)

data class Variable(val name: String, val value: String)

open class LibraryDefinition(
        val dependencies: List<String>,
        val initCell: List<String>,
        val imports: List<String>,
        val repositories: List<String>,
        val init: List<String>,
        val renderers: List<TypeHandler>,
        val converters: List<TypeHandler>,
        val annotations: List<TypeHandler>
)

class LibraryDescriptor(dependencies: List<String>,
                        val variables: List<Variable>,
                        initCell: List<String>,
                        imports: List<String>,
                        repositories: List<String>,
                        init: List<String>,
                        renderers: List<TypeHandler>,
                        converters: List<TypeHandler>,
                        annotations: List<TypeHandler>,
                        val link: String?) : LibraryDefinition(dependencies, initCell, imports, repositories, init, renderers, converters, annotations)

data class ResolverConfig(val repositories: List<RepositoryCoordinates>,
                          val libraries: Deferred<Map<String, LibraryDescriptor>>)

fun parseLibraryArgument(str: String): Variable {
    val eq = str.indexOf('=')
    return if (eq == -1) Variable("", str.trim())
    else Variable(str.substring(0, eq).trim(), str.substring(eq + 1).trim())
}

fun parseLibraryName(str: String): Pair<String, List<Variable>> {
    val brackets = str.indexOf('(')
    if (brackets == -1) return str.trim() to emptyList()
    val name = str.substring(0, brackets).trim()
    val args = str.substring(brackets + 1, str.indexOf(')', brackets))
            .split(',')
            .map(::parseLibraryArgument)
    return name to args
}

fun readLibraries(basePath: String? = null, filter: (File) -> Boolean = { true }): List<Pair<String, JsonObject>> {
    val parser = Parser.default()
    return File(basePath, LibrariesDir)
            .listFiles()?.filter { it.extension == LibraryDescriptorExt && filter(it) }
            ?.map {
                log.info("Loading '${it.nameWithoutExtension}' descriptor from '${it.canonicalPath}'")
                it.nameWithoutExtension to parser.parse(it.canonicalPath) as JsonObject
            }
            .orEmpty()
}

fun getLatestCommitToLibraries(sinceTimestamp: String?): Pair<String, String>? =
        log.catchAll {
            var url = "$GitHubApiPrefix/commits?path=$LibrariesDir&sha=$GitHubBranchName"
            if (sinceTimestamp != null)
                url += "&since=$sinceTimestamp"
            log.info("Checking for new commits to library descriptors at $url")
            val arr = getHttp(url).jsonArray
            if (arr.length() == 0) {
                if (sinceTimestamp != null)
                    getLatestCommitToLibraries(null)
                else {
                    log.info("Didn't find any commits to '$LibrariesDir' at $url")
                    null
                }
            } else {
                val commit = arr[0] as JSONObject
                val sha = commit["sha"] as String
                val timestamp = ((commit["commit"] as JSONObject)["committer"] as JSONObject)["date"] as String
                sha to timestamp
            }
        }

fun getHttp(url: String): Response {
    val response = khttp.get(url)
    if (response.statusCode != 200)
        throw Exception("Http request failed. Url = $url. Response = $response")
    return response
}

fun getLibraryDescriptorVersion(commitSha: String) =
        log.catchAll {
            val url = "$GitHubApiPrefix/contents/$LibrariesDir/$LibraryPropertiesFile?ref=$commitSha"
            log.info("Checking current library descriptor format version from $url")
            val response = getHttp(url)
            val downloadUrl = response.jsonObject["download_url"].toString()
            val downloadResult = getHttp(downloadUrl)
            val result = downloadResult.text.parseIniConfig()["formatVersion"]!!.toInt()
            log.info("Current library descriptor format version: $result")
            result
        }

/***
 * Downloads library descriptors from GitHub to local cache if new commits in `libraries` directory were detected
 */
fun downloadNewLibraryDescriptors() {

    // Read commit hash and timestamp for locally cached libraries.
    // Timestamp is used as parameter for commits request to reduce output

    val footprintFilePath = Paths.get(LocalSettingsPath, LocalCacheDir, CachedLibrariesFootprintFile).toString()
    log.info("Reading commit info for which library descriptors were cached: '$footprintFilePath'")
    val footprintFile = File(footprintFilePath)
    val footprint = footprintFile.tryReadIniConfig()
    val timestampRegex = """\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""".toRegex()
    val syncedCommitTimestamp = footprint?.get("timestamp")?.validOrNull { timestampRegex.matches(it) }
    val syncedCommitSha = footprint?.get("sha")
    log.info("Local libraries are cached for commit '$syncedCommitSha' at '$syncedCommitTimestamp'")

    val (latestCommitSha, latestCommitTimestamp) = getLatestCommitToLibraries(syncedCommitTimestamp) ?: return
    if (latestCommitSha.equals(syncedCommitSha)) {
        log.info("No new commits to library descriptors were detected")
        return
    }

    // Download library descriptor version

    val descriptorVersion = getLibraryDescriptorVersion(latestCommitSha) ?: return
    if (descriptorVersion != libraryDescriptorFormatVersion) {
        if (descriptorVersion < libraryDescriptorFormatVersion)
            log.error("Incorrect library descriptor version in GitHub repository: $descriptorVersion")
        else
            log.warn("Kotlin Kernel needs to be updated to the latest version. Couldn't download new library descriptors from GitHub repository because their format was changed")
        return
    }

    // Download library descriptors

    log.info("New commits to library descriptors were detected. Downloading library descriptors for commit $latestCommitSha")

    val libraries = log.catchAll {
        val url = "$GitHubApiPrefix/contents/$LibrariesDir?ref=$latestCommitSha"
        log.info("Requesting the list of library descriptors at $url")
        val response = getHttp(url)
        val filenameRegex = """[\w.-]+\.$LibraryDescriptorExt""".toRegex()

        response.jsonArray.mapNotNull {
            val o = it as JSONObject
            val filename = o["name"] as String
            if (filenameRegex.matches(filename)) {
                val libUrl = o["download_url"].toString()
                log.info("Downloading '$filename' from $libUrl")
                val res = getHttp(libUrl)
                val text = res.jsonObject.toString()
                filename to text
            } else null
        }
    } ?: return

    // Save library descriptors to local cache

    val librariesPath = Paths.get(LocalSettingsPath, LocalCacheDir, LibrariesDir)
    val librariesDir = librariesPath.toFile()
    log.info("Saving ${libraries.count()} library descriptors to local cache at '$librariesPath'")
    try {
        FileUtils.deleteDirectory(librariesDir)
        Files.createDirectories(librariesPath)
        libraries.forEach {
            File(librariesDir.toString(), it.first).writeText(it.second)
        }
        footprintFile.writeText("""
            timestamp=$latestCommitTimestamp
            sha=$latestCommitSha
        """.trimIndent())
    } catch (e: Exception) {
        log.error("Failed to write downloaded library descriptors to local cache:", e)
        log.catchAll { FileUtils.deleteDirectory(librariesDir) }
    }
}

fun getLibrariesJsons(homeDir: String): Map<String, JsonObject> {

    downloadNewLibraryDescriptors()

    val pathsToCheck = arrayOf(LocalSettingsPath,
            Paths.get(LocalSettingsPath, LocalCacheDir).toString(),
            homeDir)

    val librariesMap = mutableMapOf<String, JsonObject>()

    pathsToCheck.forEach {
        readLibraries(it) { !librariesMap.containsKey(it.nameWithoutExtension) }
                .forEach { librariesMap.put(it.first, it.second) }
    }

    return librariesMap
}

fun loadResolverConfig(homeDir: String) = ResolverConfig(defaultRepositories, GlobalScope.async {
    log.catchAll {
        parserLibraryDescriptors(getLibrariesJsons(homeDir))
    } ?: emptyMap()
})

val defaultRepositories = arrayOf(
        "https://jcenter.bintray.com/",
        "https://repo.maven.apache.org/maven2/",
        "https://jitpack.io"
).map { RepositoryCoordinates(it) }

fun parserLibraryDescriptors(libJsons: Map<String, JsonObject>): Map<String, LibraryDescriptor> {
    return libJsons.mapValues {
        log.info("Parsing '${it.key}' descriptor")
        LibraryDescriptor(
                dependencies = it.value.array<String>("dependencies")?.toList().orEmpty(),
                variables = it.value.obj("properties")?.map { Variable(it.key, it.value.toString()) }.orEmpty(),
                imports = it.value.array<String>("imports")?.toList().orEmpty(),
                repositories = it.value.array<String>("repositories")?.toList().orEmpty(),
                init = it.value.array<String>("init")?.toList().orEmpty(),
                initCell = it.value.array<String>("initCell")?.toList().orEmpty(),
                renderers = it.value.obj("renderers")?.map {
                    TypeHandler(it.key, it.value.toString())
                }?.toList().orEmpty(),
                link = it.value.string("link"),
                converters = it.value.obj("typeConverters")?.map { TypeHandler(it.key, it.value.toString()) }.orEmpty(),
                annotations = it.value.obj("annotationHandlers")?.map { TypeHandler(it.key, it.value.toString()) }.orEmpty()
        )
    }
}

