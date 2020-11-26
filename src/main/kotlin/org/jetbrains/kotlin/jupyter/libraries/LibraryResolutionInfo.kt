package org.jetbrains.kotlin.jupyter.libraries

import org.jetbrains.kotlin.jupyter.GitHubApiPrefix
import org.jetbrains.kotlin.jupyter.LibrariesDir
import org.jetbrains.kotlin.jupyter.LibraryDescriptorExt
import org.jetbrains.kotlin.jupyter.ReplCompilerException
import org.jetbrains.kotlin.jupyter.Variable
import org.jetbrains.kotlin.jupyter.getHttp
import org.jetbrains.kotlin.jupyter.log
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

abstract class LibraryResolutionInfo(
    private val typeKey: String
) : LibraryCacheable {
    class ByNothing : LibraryResolutionInfo("nothing") {
        override val args: List<Variable> = listOf()

        override fun resolve(name: String?): String = "{}"
    }

    class ByURL(val url: URL) : LibraryResolutionInfo("url") {
        override val args = listOf(Variable("url", url.toString()))
        override val shouldBeCachedLocally get() = false

        override fun resolve(name: String?): String {
            val response = getHttp(url.toString())
            return response.text
        }
    }

    class ByFile(val file: File) : LibraryResolutionInfo("file") {
        override val args = listOf(Variable("file", file.path))
        override val shouldBeCachedLocally get() = false

        override fun resolve(name: String?): String {
            return file.readText()
        }
    }

    class ByDir(private val librariesDir: File) : LibraryResolutionInfo("bundled") {
        override val args = listOf(Variable("dir", librariesDir.path))
        override val shouldBeCachedLocally get() = false

        override fun resolve(name: String?): String {
            if (name == null) throw ReplCompilerException("Directory library resolver needs library name to be specified")

            return librariesDir.resolve("$name.$LibraryDescriptorExt").readText()
        }
    }

    class ByGitRef(private val ref: String) : LibraryResolutionInfo("ref") {
        override val valueKey: String
            get() = sha

        val sha: String by lazy {
            val (resolvedSha, _) = getLatestCommitToLibraries(ref, null) ?: return@lazy ref
            resolvedSha
        }

        override val args = listOf(Variable("ref", ref))

        override fun resolve(name: String?): String {
            if (name == null) throw ReplCompilerException("Reference library resolver needs name to be specified")

            val url = "$GitHubApiPrefix/contents/$LibrariesDir/$name.$LibraryDescriptorExt?ref=$sha"
            log.info("Requesting library descriptor at $url")
            val response = getHttp(url).jsonObject

            val downloadURL = response["download_url"].toString()
            val res = getHttp(downloadURL)
            val text = res.jsonObject
            return text.toString()
        }
    }

    protected abstract val args: List<Variable>
    protected open val valueKey: String
        get() = args.joinToString { it.value }

    val key: String by lazy { "${typeKey}_${replaceForbiddenChars(valueKey)}" }
    abstract fun resolve(name: String?): String

    override fun hashCode(): Int {
        return key.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LibraryResolutionInfo

        return valueKey == other.valueKey
    }

    override fun toString(): String {
        return typeKey +
            when {
                args.isEmpty() -> ""
                args.size == 1 -> "[${args[0].value}]"
                else -> args.joinToString(", ", "[", "]") { "${it.name}=${it.value}" }
            }
    }

    companion object {
        private val gitRefsCache = ConcurrentHashMap<String, ByGitRef>()

        fun getInfoByRef(ref: String): ByGitRef {
            return gitRefsCache.getOrPut(ref, { ByGitRef(ref) })
        }

        fun replaceForbiddenChars(string: String): String {
            return string.replace("""[<>/\\:"|?*]""".toRegex(), "_")
        }
    }
}
