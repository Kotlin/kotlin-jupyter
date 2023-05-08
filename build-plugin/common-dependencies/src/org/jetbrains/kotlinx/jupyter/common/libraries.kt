package org.jetbrains.kotlinx.jupyter.common

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

fun interface ExceptionsHandler {
    fun handle(logger: Logger, message: String, exception: Throwable)

    object DEFAULT : ExceptionsHandler {
        override fun handle(logger: Logger, message: String, exception: Throwable) {
            logger.error(message)
            throw exception
        }
    }
}

class LibraryDescriptorsManager private constructor(
    user: String,
    repo: String,
    private val remotePath: String,
    localPath: String,
    private val homePath: String,
    userPath: String,
    private val resourcesPath: String,
    private val exceptionsHandler: ExceptionsHandler,
    userSettingsDir: File,
    private val logger: Logger,
) {
    private val authUser: String? = System.getenv("KOTLIN_JUPYTER_GITHUB_USER")
    private val authToken: String? = System.getenv("KOTLIN_JUPYTER_GITHUB_TOKEN")
    private val apiPrefix = "https://$GITHUB_API_HOST/repos/$user/$repo"
    val userLibrariesDir = userSettingsDir.resolve(userPath)
    val userCacheDir = userSettingsDir.resolve("cache")
    val localLibrariesDir = File(localPath)
    val defaultBranch = "master"
    val latestCommitOnDefaultBranch get() = getLatestCommitToLibraries(defaultBranch)?.sha

    fun homeLibrariesDir(homeDir: File? = null) = (homeDir ?: File("")).resolve(homePath)

    val commitHashFile by lazy {
        localLibrariesDir.resolve(COMMIT_HASH_FILE).also { file ->
            if (!file.exists()) {
                file.createDirsAndWrite()
            }
        }
    }

    fun descriptorFileName(name: String) = "$name.$DESCRIPTOR_EXTENSION"
    fun optionsFileName() = OPTIONS_FILE

    fun resourceLibraryPath(name: String) = "$resourcesPath/${descriptorFileName(name)}"
    fun resourceOptionsPath() = "$resourcesPath/${optionsFileName()}"

    fun isLibraryDescriptor(file: File): Boolean {
        return file.isFile && file.name.endsWith(".$DESCRIPTOR_EXTENSION")
    }

    fun getLatestCommitToLibraries(ref: String, sinceTimestamp: String? = null): CommitInfo? {
        return catchAll {
            var url = "$apiPrefix/commits?path=$remotePath&sha=$ref"
            if (sinceTimestamp != null) {
                url += "&since=$sinceTimestamp"
            }
            logger.info("Checking for new commits to library descriptors at $url")
            val arr = getGithubHttpWithAuth(url).jsonArrayOrNull
            if (arr == null) {
                logger.error("Request for the latest commit in libraries failed")
                return@catchAll null
            }
            if (arr.isEmpty()) {
                if (sinceTimestamp != null) {
                    getLatestCommitToLibraries(ref, null)
                } else {
                    logger.info("Didn't find any commits to libraries at $url")
                    null
                }
            } else {
                val commit = arr[0] as JsonObject
                val sha = (commit["sha"] as JsonPrimitive).content
                val timestamp = (((commit["commit"] as JsonObject)["committer"] as JsonObject)["date"] as JsonPrimitive).content
                CommitInfo(sha, timestamp)
            }
        }
    }

    fun downloadGlobalDescriptorOptions(ref: String): String? {
        val url = "$apiPrefix/contents/$remotePath/$OPTIONS_FILE?ref=$ref"
        logger.info("Requesting global descriptor options at $url")
        return try {
            downloadSingleFile(url)
        } catch (e: Throwable) {
            logger.warn("Unable to load global descriptor options", e)
            null
        }
    }

    fun downloadLibraryDescriptor(ref: String, name: String): String {
        val url = "$apiPrefix/contents/$remotePath/$name.$DESCRIPTOR_EXTENSION?ref=$ref"
        logger.info("Requesting library descriptor at $url")
        return downloadSingleFile(url)
    }

    fun checkRefExistence(ref: String): Boolean {
        val response = getGithubHttpWithAuth("$apiPrefix/contents/$remotePath?ref=$ref")
        return response.status.successful
    }

    fun checkIfRefUpToDate(remoteRef: String?): Boolean {
        if (!commitHashFile.exists()) return false
        if (remoteRef == null) {
            logger.warn("Considering reference up-to-date because getting the last reference failed")
            return true
        }
        val localRef = commitHashFile.readText()
        return localRef == remoteRef
    }

    fun downloadLibraries(ref: String) {
        localLibrariesDir.mkdirs()

        val url = "$apiPrefix/contents/$remotePath?ref=$ref"
        logger.info("Requesting library descriptors at $url")
        val response = getGithubHttpWithAuth(url).jsonArray

        for (item in response) {
            item as JsonObject
            if (item["type"]?.jsonPrimitive?.content != "file") continue

            val fileName = item["name"]!!.jsonPrimitive.content
            if (!fileName.endsWith(".$DESCRIPTOR_EXTENSION") && fileName != OPTIONS_FILE) continue

            val downloadUrl = item["download_url"]!!.jsonPrimitive.content
            val descriptorResponse = getHttp(downloadUrl)

            val descriptorText = descriptorResponse.text
            val file = localLibrariesDir.resolve(fileName)
            file.writeText(descriptorText)
        }

        saveLocalRef(ref)
    }

    private fun getGithubHttpWithAuth(url: String): ResponseWrapper {
        return if (authToken == null || authUser == null) {
            getHttp(url)
        } else {
            getHttpWithAuth(url, authUser, authToken)
        }
    }

    private fun downloadSingleFile(contentsApiUrl: String): String {
        val response = getGithubHttpWithAuth(contentsApiUrl).jsonObject
        val downloadUrl = response["download_url"]!!.jsonPrimitive.content
        val res = getHttp(downloadUrl)
        res.assertSuccessful()
        return res.text
    }

    private fun saveLocalRef(ref: String) {
        commitHashFile.createDirsAndWrite(ref)
    }

    private fun File.createDirsAndWrite(text: String = "") {
        parentFile.mkdirs()
        writeText(text)
    }

    private fun <T> catchAll(message: String = "", body: () -> T): T? = try {
        body()
    } catch (e: Throwable) {
        exceptionsHandler.handle(logger, message, e)
        null
    }

    class CommitInfo(
        val sha: String,
        val timestamp: String,
    )

    companion object {
        private const val GITHUB_API_HOST = "api.github.com"
        private const val DESCRIPTOR_EXTENSION = "json"
        private const val COMMIT_HASH_FILE = "commit_sha"
        private const val OPTIONS_FILE = "global.options"

        fun getInstance(
            logger: Logger = LoggerFactory.getLogger(LibraryDescriptorsManager::class.java),
            exceptionsHandler: ExceptionsHandler = ExceptionsHandler.DEFAULT,
        ): LibraryDescriptorsManager {
            return LibraryDescriptorsManager(
                "Kotlin",
                "kotlin-jupyter-libraries",
                "",
                "libraries",
                "libraries",
                "libraries",
                "jupyterLibraries",
                exceptionsHandler,
                File(System.getProperty("user.home")).resolve(".jupyter_kotlin"),
                logger,
            )
        }
    }
}
