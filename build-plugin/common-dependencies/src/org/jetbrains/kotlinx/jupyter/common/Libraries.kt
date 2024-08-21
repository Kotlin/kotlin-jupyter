package org.jetbrains.kotlinx.jupyter.common

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.Logger
import java.io.File

fun interface ExceptionsHandler {
    fun handle(
        logger: Logger,
        message: String,
        exception: Throwable,
    )

    object DEFAULT : ExceptionsHandler {
        override fun handle(
            logger: Logger,
            message: String,
            exception: Throwable,
        ) {
            logger.error(message)
            throw exception
        }
    }
}

fun interface CommonLoggerFactory {
    fun getLogger(clazz: Class<*>): Logger
}

interface LibraryDescriptorsManager {
    val userLibrariesDir: File
    val userCacheDir: File
    val localLibrariesDir: File
    val defaultBranch: String
    val latestCommitOnDefaultBranch: String?
    val commitHashFile: File

    fun homeLibrariesDir(homeDir: File? = null): File

    fun descriptorFileName(name: String): String

    fun optionsFileName(): String

    fun resourceLibraryPath(name: String): String

    fun resourceOptionsPath(): String

    fun isLibraryDescriptor(file: File): Boolean

    fun getLatestCommitToLibraries(
        ref: String,
        sinceTimestamp: String? = null,
    ): CommitInfo?

    fun downloadGlobalDescriptorOptions(ref: String): String?

    fun downloadLibraryDescriptor(
        ref: String,
        name: String,
    ): String

    fun checkRefExistence(ref: String): Boolean

    fun checkIfRefUpToDate(remoteRef: String?): Boolean

    fun downloadLibraries(ref: String)

    class CommitInfo(
        val sha: String,
        val timestamp: String,
    )

    companion object {
        fun getInstance(
            httpClient: HttpClient,
            loggerFactory: CommonLoggerFactory,
            exceptionsHandler: ExceptionsHandler = ExceptionsHandler.DEFAULT,
        ): LibraryDescriptorsManager {
            return LibraryDescriptorsManagerImpl(
                "Kotlin",
                "kotlin-jupyter-libraries",
                "",
                "libraries",
                "libraries",
                "libraries",
                "jupyterLibraries",
                exceptionsHandler,
                File(System.getProperty("user.home")).resolve(".jupyter_kotlin"),
                httpClient,
                loggerFactory,
            )
        }
    }
}

private class LibraryDescriptorsManagerImpl(
    user: String,
    repo: String,
    private val remotePath: String,
    localPath: String,
    private val homePath: String,
    userPath: String,
    private val resourcesPath: String,
    private val exceptionsHandler: ExceptionsHandler,
    userSettingsDir: File,
    private val httpClient: HttpClient,
    loggerFactory: CommonLoggerFactory,
) : LibraryDescriptorsManager {
    private val logger = loggerFactory.getLogger(this::class.java)

    private val authUser: String? = System.getenv("KOTLIN_JUPYTER_GITHUB_USER")
    private val authToken: String? = System.getenv("KOTLIN_JUPYTER_GITHUB_TOKEN")
    private val apiPrefix = "https://$GITHUB_API_HOST/repos/$user/$repo"
    override val userLibrariesDir = userSettingsDir.resolve(userPath)
    override val userCacheDir = userSettingsDir.resolve("cache")
    override val localLibrariesDir = File(localPath)
    override val defaultBranch = "master"
    override val latestCommitOnDefaultBranch get() = getLatestCommitToLibraries(defaultBranch)?.sha

    override fun homeLibrariesDir(homeDir: File?) = (homeDir ?: File("")).resolve(homePath)

    override val commitHashFile by lazy {
        localLibrariesDir.resolve(COMMIT_HASH_FILE).also { file ->
            if (!file.exists()) {
                file.createDirsAndWrite()
            }
        }
    }

    override fun descriptorFileName(name: String) = "$name.$DESCRIPTOR_EXTENSION"

    override fun optionsFileName() = OPTIONS_FILE

    override fun resourceLibraryPath(name: String) = "$resourcesPath/${descriptorFileName(name)}"

    override fun resourceOptionsPath() = "$resourcesPath/${optionsFileName()}"

    override fun isLibraryDescriptor(file: File): Boolean {
        return file.isFile && file.name.endsWith(".$DESCRIPTOR_EXTENSION")
    }

    override fun getLatestCommitToLibraries(
        ref: String,
        sinceTimestamp: String?,
    ): LibraryDescriptorsManager.CommitInfo? {
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
                LibraryDescriptorsManager.CommitInfo(sha, timestamp)
            }
        }
    }

    override fun downloadGlobalDescriptorOptions(ref: String): String? {
        val url = resolveAgainstRemotePath(OPTIONS_FILE, ref)
        logger.info("Requesting global descriptor options at $url")
        return try {
            downloadSingleFile(url)
        } catch (e: Throwable) {
            logger.warn("Unable to load global descriptor options", e)
            null
        }
    }

    override fun downloadLibraryDescriptor(
        ref: String,
        name: String,
    ): String {
        val url = resolveAgainstRemotePath("$name.$DESCRIPTOR_EXTENSION", ref)
        logger.info("Requesting library descriptor at $url")
        return downloadSingleFile(url)
    }

    override fun checkRefExistence(ref: String): Boolean {
        val response = getGithubHttpWithAuth(resolveAgainstRemotePath("", ref))
        return response.status.successful
    }

    private fun resolveAgainstRemotePath(
        filePath: String,
        ref: String,
    ): String {
        return buildString {
            append(apiPrefix)
            append("/contents")
            if (remotePath.isNotEmpty()) {
                append("/")
                append(remotePath)
            }
            if (filePath.isNotEmpty()) {
                append("/")
                append(filePath)
            }
            append("?ref=")
            append(ref)
        }
    }

    override fun checkIfRefUpToDate(remoteRef: String?): Boolean {
        if (!commitHashFile.exists()) return false
        if (remoteRef == null) {
            logger.warn("Considering reference up-to-date because getting the last reference failed")
            return true
        }
        val localRef = commitHashFile.readText()
        return localRef == remoteRef
    }

    override fun downloadLibraries(ref: String) {
        localLibrariesDir.deleteRecursively()
        localLibrariesDir.mkdirs()

        val url = resolveAgainstRemotePath("", ref)
        logger.info("Requesting library descriptors at $url")
        val response = getGithubHttpWithAuth(url).jsonArray

        for (item in response) {
            item as JsonObject
            if (item["type"]?.jsonPrimitive?.content != "file") continue

            val fileName = item["name"]!!.jsonPrimitive.content
            if (!fileName.endsWith(".$DESCRIPTOR_EXTENSION") && fileName != OPTIONS_FILE) continue

            val downloadUrl = item["download_url"]!!.jsonPrimitive.content
            val descriptorResponse = httpClient.getHttp(downloadUrl)

            val descriptorText = descriptorResponse.text
            val file = localLibrariesDir.resolve(fileName)
            file.writeText(descriptorText)
        }

        saveLocalRef(ref)
    }

    private fun getGithubHttpWithAuth(url: String): ResponseWrapper {
        return if (authToken == null || authUser == null) {
            httpClient.getHttp(url)
        } else {
            httpClient.getHttpWithAuth(url, authUser, authToken)
        }
    }

    private fun downloadSingleFile(contentsApiUrl: String): String {
        val response = getGithubHttpWithAuth(contentsApiUrl).jsonObject
        val downloadUrl = response["download_url"]!!.jsonPrimitive.content
        val res = httpClient.getHttp(downloadUrl)
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

    private fun <T> catchAll(
        message: String = "",
        body: () -> T,
    ): T? =
        try {
            body()
        } catch (e: Throwable) {
            exceptionsHandler.handle(logger, message, e)
            null
        }

    companion object {
        private const val GITHUB_API_HOST = "api.github.com"
        private const val DESCRIPTOR_EXTENSION = "json"
        private const val COMMIT_HASH_FILE = "commit_sha"
        private const val OPTIONS_FILE = "global.options"
    }
}
