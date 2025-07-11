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
    )

    companion object {
        fun getInstance(
            httpClient: HttpClient,
            loggerFactory: CommonLoggerFactory,
            exceptionsHandler: ExceptionsHandler = ExceptionsHandler.DEFAULT,
        ): LibraryDescriptorsManager =
            LibraryDescriptorsManagerImpl(
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
    private val githubApiPrefix = "https://$GITHUB_API_HOST/repos/$user/$repo"
    private val rawContentApiPrefix = "https://$RAW_GITHUB_CONTENT_HOST/$user/$repo"
    override val userLibrariesDir = userSettingsDir.resolve(userPath)
    override val localLibrariesDir = File(localPath)
    override val defaultBranch = "master"
    override val latestCommitOnDefaultBranch by lazy {
        getLatestCommitToLibraries(defaultBranch)?.sha
    }

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

    override fun isLibraryDescriptor(file: File): Boolean = file.isFile && file.name.endsWith(".$DESCRIPTOR_EXTENSION")

    override fun getLatestCommitToLibraries(
        ref: String,
        sinceTimestamp: String?,
    ): LibraryDescriptorsManager.CommitInfo? {
        return catchAll {
            var url = "$githubApiPrefix/commits?path=$remotePath&sha=$ref"
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
                LibraryDescriptorsManager.CommitInfo(sha)
            }
        }
    }

    override fun downloadGlobalDescriptorOptions(ref: String): String? {
        logger.info("Downloading global descriptor options")
        return try {
            downloadFileDirectly(OPTIONS_FILE, ref)
        } catch (e: Throwable) {
            logger.warn("Unable to load global descriptor options", e)
            null
        }
    }

    override fun downloadLibraryDescriptor(
        ref: String,
        name: String,
    ): String {
        val fileName = "$name.$DESCRIPTOR_EXTENSION"
        logger.info("Downloading library descriptor $fileName")
        return downloadFileDirectly(fileName, ref)
    }

    override fun checkRefExistence(ref: String): Boolean =
        // We suppose that each self-respecting git ref in
        // libraries repo should have at least one of these files
        listOf(".properties", "LICENSE").any { fileName ->
            val url = buildRawContentApiDownloadUrl(fileName, ref)
            logger.info("Checking ref existence directly at $url")
            val response = httpClient.getHttp(url)
            response.status.successful
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

        val url = buildGithubApiDownloadUrl("", ref)
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
        val response =
            if (authToken == null || authUser == null) {
                httpClient.getHttp(url)
            } else {
                httpClient.getHttpWithAuth(url, authUser, authToken)
            }

        if (logger.isDebugEnabled && !response.status.successful) {
            logger.debug("Request to GitHub API '$url' failed, response:\n${response.text}")
        }

        return response
    }

    private fun downloadFileDirectly(
        filePath: String,
        ref: String,
    ): String {
        val url = buildRawContentApiDownloadUrl(filePath, ref)
        logger.info("Directly downloading file from $url")
        val res = httpClient.getHttp(url)
        res.assertSuccessful()
        return res.text
    }

    private fun buildRawContentApiDownloadUrl(
        filePath: String,
        ref: String,
    ): String =
        buildString {
            append(rawContentApiPrefix)
            append("/")
            append(ref)
            if (remotePath.isNotEmpty()) {
                append("/")
                append(remotePath)
            }
            if (filePath.isNotEmpty()) {
                append("/")
                append(filePath)
            }
        }

    private fun buildGithubApiDownloadUrl(
        @Suppress("SameParameterValue") filePath: String,
        ref: String,
    ): String =
        buildString {
            append(githubApiPrefix)
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
        private const val RAW_GITHUB_CONTENT_HOST = "raw.githubusercontent.com"
        private const val DESCRIPTOR_EXTENSION = "json"
        private const val COMMIT_HASH_FILE = "commit_sha"
        private const val OPTIONS_FILE = "global.options"
    }
}
