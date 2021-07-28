package org.jetbrains.kotlinx.jupyter.libraries

import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

abstract class LibraryResolutionInfo(
    private val typeKey: String
) : LibraryCacheable {
    class ByNothing : LibraryResolutionInfo("nothing") {
        override val args: List<Variable> = listOf()
    }

    class ByURL(val url: URL) : LibraryResolutionInfo("url") {
        override val args = listOf(Variable("url", url.toString()))
        override val shouldBeCachedLocally get() = false
    }

    class ByFile(val file: File) : LibraryResolutionInfo("file") {
        override val args = listOf(Variable("file", file.path))
        override val shouldBeCachedLocally get() = false
    }

    class ByDir(val librariesDir: File) : LibraryResolutionInfo("bundled") {
        override val args = listOf(Variable("dir", librariesDir.path))
        override val shouldBeCachedLocally get() = false
    }

    class ByGitRef(private val ref: String) : LibraryResolutionInfo("ref") {
        override val valueKey: String
            get() = sha

        val sha: String by lazy {
            val (resolvedSha, _) = KERNEL_LIBRARIES.getLatestCommitToLibraries(ref, null) ?: return@lazy ref
            resolvedSha
        }

        override val args = listOf(Variable("ref", ref))
    }

    class Default(val string: String = "") : LibraryResolutionInfo("default") {
        override val args: List<Variable> = listOf()
        override val shouldBeCachedLocally get() = false
    }

    protected abstract val args: List<Variable>
    protected open val valueKey: String
        get() = args.joinToString { it.value }

    val key: String by lazy { "${typeKey}_${replaceForbiddenChars(valueKey)}" }

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
