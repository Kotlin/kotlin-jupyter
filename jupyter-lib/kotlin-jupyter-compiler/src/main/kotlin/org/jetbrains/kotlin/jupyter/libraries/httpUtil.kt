package org.jetbrains.kotlin.jupyter.libraries

import khttp.responses.Response
import org.jetbrains.kotlin.jupyter.config.catchAll
import org.jetbrains.kotlin.jupyter.config.getLogger
import org.json.JSONObject

fun getHttp(url: String): Response {
    val response = khttp.get(url)
    if (response.statusCode != 200)
        throw Exception("Http request failed. Url = $url. Response = $response")
    return response
}

fun getLatestCommitToLibraries(ref: String, sinceTimestamp: String?): Pair<String, String>? {
    val logger = getLogger()
    return logger.catchAll {
        var url = "$GitHubApiPrefix/commits?path=$LibrariesDir&sha=$ref"
        if (sinceTimestamp != null)
            url += "&since=$sinceTimestamp"
        logger.info("Checking for new commits to library descriptors at $url")
        val arr = getHttp(url).jsonArray
        if (arr.length() == 0) {
            if (sinceTimestamp != null)
                getLatestCommitToLibraries(ref, null)
            else {
                logger.info("Didn't find any commits to '$LibrariesDir' at $url")
                null
            }
        } else {
            val commit = arr[0] as JSONObject
            val sha = commit["sha"] as String
            val timestamp = ((commit["commit"] as JSONObject)["committer"] as JSONObject)["date"] as String
            sha to timestamp
        }
    }
}
