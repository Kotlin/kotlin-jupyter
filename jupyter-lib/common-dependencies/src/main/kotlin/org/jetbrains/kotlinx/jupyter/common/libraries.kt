package org.jetbrains.kotlinx.jupyter.common

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    private val exceptionsHandler: ExceptionsHandler,
    userSettingsDir: File,
    private val logger: Logger,
) {
    private val apiPrefix = "https://$GITHUB_API_HOST/repos/$user/$repo"
    val userLibrariesDir = userSettingsDir.resolve(userPath)
    val userCacheDir = userSettingsDir.resolve("cache")
    val localLibrariesDir = File(localPath)
    fun homeLibrariesDir(homeDir: File? = null) = (homeDir ?: File("")).resolve(homePath)

    val localPropertiesFile = localLibrariesDir.resolve(PROPERTIES_FILE)

    fun descriptorFileName(name: String) = "$name.$DESCRIPTOR_EXTENSION"

    fun isLibraryDescriptor(file: File): Boolean {
        return file.isFile && file.name.endsWith(".$DESCRIPTOR_EXTENSION")
    }

    fun getLatestCommitToLibraries(ref: String, sinceTimestamp: String?): Pair<String, String>? {
        return catchAll {
            var url = "$apiPrefix/commits?path=$remotePath&sha=$ref"
            if (sinceTimestamp != null) {
                url += "&since=$sinceTimestamp"
            }
            logger.info("Checking for new commits to library descriptors at $url")
            val arr = getHttp(url).jsonArray
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
                sha to timestamp
            }
        }
    }

    fun downloadLibraryDescriptor(ref: String, name: String): String {
        val url = "$apiPrefix/contents/$remotePath/$name.$DESCRIPTOR_EXTENSION?ref=$ref"
        logger.info("Requesting library descriptor at $url")
        val response = getHttp(url).jsonObject

        val downloadURL = (response["download_url"] as JsonPrimitive).content
        val res = getHttp(downloadURL)
        return res.text
    }

    fun checkRefExistence(ref: String): Boolean {
        val response = getHttp("$apiPrefix/contents/$remotePath?ref=$ref")
        return response.status.successful
    }

    private fun <T> catchAll(message: String = "", body: () -> T): T? = try {
        body()
    } catch (e: Throwable) {
        exceptionsHandler.handle(logger, message, e)
        null
    }

    companion object {
        private const val GITHUB_API_HOST = "api.github.com"
        private const val DESCRIPTOR_EXTENSION = "json"
        private const val PROPERTIES_FILE = ".properties"

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
                exceptionsHandler,
                File(System.getProperty("user.home")).resolve(".jupyter_kotlin"),
                logger
            )
        }
    }
}
