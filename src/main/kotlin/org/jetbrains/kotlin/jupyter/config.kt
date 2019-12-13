package org.jetbrains.kotlin.jupyter

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.io.File
import java.io.StringReader
import java.nio.file.Paths
import kotlin.script.experimental.dependencies.RepositoryCoordinates

val LibrariesDir = "libraries"

val LocalSettingsPath = Paths.get(System.getProperty("user.home"), ".jupyter_kotlin").toString()

val GitHubApiHost = "api.github.com"
val GitHubRepoOwner = "kotlin"
val GitHubRepoName = "kotlin-jupyter"
val GitHubApiPrefix = "https://$GitHubApiHost/repos/$GitHubRepoOwner/$GitHubRepoName/contents/"

internal val log by lazy { LoggerFactory.getLogger("ikotlin") }

enum class JupyterSockets {
    hb,
    shell,
    control,
    stdin,
    iopub
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

data class TypeRenderer(val className: String, val displayCode: String?, val resultCode: String?)

data class Variable(val name: String?, val value: String?)

class LibraryDefinition(val dependencies: List<String>,
                        val variables: List<Variable>,
                        val initCell: List<String>,
                        val imports: List<String>,
                        val repositories: List<String>,
                        val init: List<String>,
                        val renderers: List<TypeRenderer>,
                        val link: String?)

data class ResolverConfig(val repositories: List<RepositoryCoordinates>,
                          val libraries: Map<String, LibraryDefinition>)

fun readJson(path: String) =
        Parser.default().parse(path) as JsonObject

fun JSONObject.toJsonObject() = Parser.default().parse(StringReader(toString())) as JsonObject

fun <T> catchAll(body: () -> T): T? = try {
    body()
} catch (e: Exception) {
    null
}

fun parseLibraryArgument(str: String): Variable {
    val eq = str.indexOf('=')
    return if (eq == -1) Variable(str.trim(), null)
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
            .listFiles()?.filter { it.extension == "json" && filter(it) }
            ?.map {
                log.info("Loading '${it.nameWithoutExtension}' descriptor from '${it.canonicalPath}'")
                it.nameWithoutExtension to parser.parse(it.canonicalPath) as JsonObject
            }
            .orEmpty()
}

fun getLibrariesJsons(homeDir: String): Map<String, JsonObject> {

    val librariesMap = readLibraries(LocalSettingsPath).toMap().orEmpty().toMutableMap()

    val address = GitHubApiPrefix + LibrariesDir
    val response = catchAll { khttp.get(address) }
    if (response != null && response.statusCode == 200) {
        response.jsonArray.forEach {
            val o = it as JSONObject
            val filename = o["name"] as String
            if (filename.endsWith(".json")) {
                val libName = filename.substring(0, filename.length - 5)
                if (!librariesMap.containsKey(libName)) {
                    val url = o["download_url"].toString()
                    val res = catchAll { khttp.get(url) }
                    if (res != null && res.statusCode == 200) {
                        log.info("Loading '$libName' descriptor from '$url'")
                        librariesMap[libName] = res.jsonObject.toJsonObject()
                    }
                }
            }
        }
    }

    readLibraries(homeDir) { !librariesMap.containsKey(it.nameWithoutExtension) }
            .forEach { librariesMap.put(it.first, it.second) }

    return librariesMap
}

fun loadResolverConfig(homeDir: String) = parseResolverConfig(getLibrariesJsons(homeDir))

val defaultRepositories = arrayOf(
        "https://jcenter.bintray.com/",
        "https://repo.maven.apache.org/maven2/",
        "https://jitpack.io"
)

fun parseResolverConfig(libJsons: Map<String, JsonObject>): ResolverConfig {
    val repos = defaultRepositories.map { RepositoryCoordinates(it) }.orEmpty()
    return ResolverConfig(repos, libJsons.mapValues {
        LibraryDefinition(
                dependencies = it.value.array<String>("dependencies")?.toList().orEmpty(),
                variables = it.value.array<String>("arguments")?.map(::parseLibraryArgument).orEmpty(),
                imports = it.value.array<String>("imports")?.toList().orEmpty(),
                repositories = it.value.array<String>("repositories")?.toList().orEmpty(),
                init = it.value.array<String>("init")?.toList().orEmpty(),
                initCell = it.value.array<String>("initCell")?.toList().orEmpty(),
                renderers = it.value.array<JsonObject>("renderers")?.map {
                    TypeRenderer(it.string("class")!!, it.string("display"), it.string("result"))
                }?.toList().orEmpty(),
                link = it.value.string("link")
        )
    })
}