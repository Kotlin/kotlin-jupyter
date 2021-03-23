package jupyter.kotlin

import java.io.File

fun clearIvyCache()= deleteCacheDir()
fun clearIvyCache(group: String) = deleteCacheDir(group)
fun clearIvyCache(group: String, artifact: String) = deleteCacheDir(group, artifact)
fun clearIvyCache(group: String, artifact: String, version: String) = deleteCacheDir(group, artifact, version)

private fun deleteCacheDir(group: String? = null, artifact: String? = null, version: String? = null) {
    val userHomeDir = File(System.getProperty("user.home"))
    val ivyCacheDir = userHomeDir.resolve(".ivy2/cache")

    fun File.deleteDir() {
        if (exists()) deleteRecursively()
    }

    var deleteDir = ivyCacheDir
    if (group == null) {
        deleteDir.deleteDir()
        return
    }

    deleteDir = deleteDir.resolve(group)
    if (artifact == null) {
        deleteDir.deleteDir()
        return
    }

    deleteDir = deleteDir.resolve(artifact)
    if (version == null) {
        deleteDir.deleteDir()
        return
    }

    val filesToDelete = listOf(
        "ivy-$version.xml",
        "ivy-$version.xml.original",
        "ivydata-$version.properties",
        "jars/$artifact-$version.jar"
    )
    filesToDelete.forEach {
        deleteDir.resolve(it).delete()
    }
}
