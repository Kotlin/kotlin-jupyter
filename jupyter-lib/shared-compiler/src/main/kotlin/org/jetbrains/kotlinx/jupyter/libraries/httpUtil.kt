package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlinx.jupyter.common.getHttp
import org.jetbrains.kotlinx.jupyter.common.jsonArray
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.config.getLogger

fun getLatestCommitToLibraries(ref: String, sinceTimestamp: String?): Pair<String, String>? {
    val logger = getLogger()
    return logger.catchAll {
        var url = "$GitHubApiPrefix/commits?path=$RemoteLibrariesDir&sha=$ref"
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
