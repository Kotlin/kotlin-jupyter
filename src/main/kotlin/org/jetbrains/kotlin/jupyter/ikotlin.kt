package org.jetbrains.kotlin.jupyter

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.script.experimental.dependencies.RepositoryCoordinates
import kotlin.script.experimental.jvm.util.classpathFromClassloader


data class KernelArgs(val cfgFile: File,
                      val scriptClasspath: List<File>,
                      val libs: File?)

private fun parseCommandLine(vararg args: String): KernelArgs {
    var cfgFile: File? = null
    var classpath: List<File>? = null
    var libsJson: File? = null
    args.forEach {
        when {
            it.startsWith("-cp=") || it.startsWith("-classpath=") -> {
                if (classpath != null) throw IllegalArgumentException("classpath already set to ${classpath!!.joinToString(File.pathSeparator)}")
                classpath = it.substringAfter('=').split(File.pathSeparator).map { File(it) }
            }
            it.startsWith("-libs=") -> {
                libsJson = File(it.substringAfter('='))
            }
            else -> {
                if (cfgFile != null) throw IllegalArgumentException("config file already set to $cfgFile")
                cfgFile = File(it)
            }
        }
    }
    if (cfgFile == null) throw IllegalArgumentException("config file is not provided")
    if (!cfgFile!!.exists() || !cfgFile!!.isFile) throw IllegalArgumentException("invalid config file $cfgFile")
    return KernelArgs(cfgFile!!, classpath ?: emptyList(), libsJson)
}

fun printClassPath() {

    val cl = ClassLoader.getSystemClassLoader()

    val cp = classpathFromClassloader(cl)

    if (cp != null)
        log.info("Current classpath: " + cp.joinToString())
}

fun parseLibraryName(str: String): Pair<String, List<ArtifactVariable>> {
    val pattern = """\w+(\w+)?""".toRegex().matches(str)
    val brackets = str.indexOf('(')
    if (brackets == -1) return str.trim() to emptyList()
    val name = str.substring(0, brackets).trim()
    val args = str.substring(brackets + 1, str.indexOf(')', brackets))
            .split(',')
            .map {
                val eq = it.indexOf('=')
                if (eq == -1) ArtifactVariable(it.trim(), null)
                else ArtifactVariable(it.substring(0, eq).trim(), it.substring(eq + 1).trim())
            }
    return name to args
}

fun parseLibrariesConfig(json: JsonObject): ResolverConfig {
    val repos = json.array<String>("repositories")?.map { RepositoryCoordinates(it) }.orEmpty()
    val artifacts = json.array<JsonObject>("libraries")?.map {
        val (name, variables) = parseLibraryName(it.string("name")!!)
        name to LibraryDefinition(
                artifacts = it.array<String>("artifacts")?.toList().orEmpty(),
                variables = variables,
                imports = it.array<String>("imports")?.toList().orEmpty(),
                initCodes = it.array<String>("initCodes")?.toList().orEmpty(),
                renderers = it.array<JsonObject>("renderers")?.map {
                    TypeRenderer(it.string("class")!!, it.string("display"), it.string("result"))
                }?.toList().orEmpty()
        )
    }?.toMap()
    return ResolverConfig(repos, artifacts.orEmpty())
}

fun main(vararg args: String) {
    try {
        log.info("Kernel args: "+ args.joinToString { it })
        val (cfgFile, scriptClasspath, librariesConfigFile) = parseCommandLine(*args)
        val cfgJson = Parser().parse(cfgFile.canonicalPath) as JsonObject
        fun JsonObject.getInt(field: String): Int = int(field) ?: throw RuntimeException("Cannot find $field in $cfgFile")

        val sigScheme = cfgJson.string("signature_scheme")
        val key = cfgJson.string("key")

        kernelServer(KernelConfig(
                ports = JupyterSockets.values().map { cfgJson.getInt("${it.name}_port") }.toTypedArray(),
                transport = cfgJson.string("transport") ?: "tcp",
                signatureScheme = sigScheme ?: "hmac1-sha256",
                signatureKey = if (sigScheme == null || key == null) "" else key,
                scriptClasspath = scriptClasspath,
                resolverConfig = librariesConfigFile?.let {
                    parseLibrariesConfig(Parser().parse(it.canonicalPath) as JsonObject)
                }
        ))
    } catch (e: Exception) {
        log.error("exception running kernel with args: \"${args.joinToString()}\"", e)
    }
}

fun kernelServer(config: KernelConfig) {
    log.info("Starting server with config: $config")

    JupyterConnection(config).use { conn ->

        printClassPath()

        log.info("Begin listening for events")

        val executionCount = AtomicLong(1)

        val repl = ReplForJupyter(conn.config.scriptClasspath, conn.config.resolverConfig)

        val mainThread = Thread.currentThread()

        val controlThread = thread {
            while (true) {
                try {
                    conn.heartbeat.onData { send(it, 0) }
                    conn.control.onMessage { shellMessagesHandler(it, null, executionCount) }

                    Thread.sleep(config.pollingIntervalMillis)
                } catch (e: InterruptedException) {
                    log.debug("Control: Interrupted")
                    mainThread.interrupt()
                    break
                }
            }
        }

        while (true) {
            try {
                conn.shell.onMessage { shellMessagesHandler(it, repl, executionCount) }

                Thread.sleep(config.pollingIntervalMillis)
            } catch (e: InterruptedException) {
                log.debug("Main: Interrupted")
                controlThread.interrupt()
                break
            }
        }

        try {
            controlThread.join()
        } catch (e: InterruptedException) {
        }

        log.info("Shutdown server")
    }
}
