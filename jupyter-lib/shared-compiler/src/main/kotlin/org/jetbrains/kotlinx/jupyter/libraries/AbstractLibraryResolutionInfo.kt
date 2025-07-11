package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionInfo
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import org.jetbrains.kotlinx.jupyter.common.LibraryDescriptorsManager
import java.io.File
import java.net.URL

abstract class AbstractLibraryResolutionInfo(
    private val typeKey: String,
) : LibraryResolutionInfo {
    class ByURL(
        val url: URL,
    ) : AbstractLibraryResolutionInfo("url") {
        override val args = listOf(Variable("url", url.toString()))
    }

    class ByFile(
        val file: File,
    ) : AbstractLibraryResolutionInfo("file") {
        override val args = listOf(Variable("file", file.path))
    }

    class ByDir(
        val librariesDir: File,
    ) : AbstractLibraryResolutionInfo("bundled") {
        override val args = listOf(Variable("dir", librariesDir.path))
    }

    open class ByGitRef(
        val ref: String,
        private val libraryDescriptorsManager: LibraryDescriptorsManager,
    ) : AbstractLibraryResolutionInfo("ref") {
        override val valueKey: String
            get() = sha

        val sha: String by lazy {
            when {
                ref == libraryDescriptorsManager.defaultBranch -> libraryDescriptorsManager.latestCommitOnDefaultBranch ?: ref
                else -> libraryDescriptorsManager.getLatestCommitToLibraries(ref, null)?.sha ?: ref
            }
        }

        override val args = listOf(Variable("ref", ref))
    }

    object ByClasspath : AbstractLibraryResolutionInfo("classpath") {
        override val args = emptyList<Variable>()
    }

    class ByGitRefWithClasspathFallback(
        ref: String,
        libraryDescriptorsManager: LibraryDescriptorsManager,
    ) : ByGitRef(ref, libraryDescriptorsManager) {
        override val valueKey: String
            get() = "fallback_" + super.valueKey
    }

    class Default(
        val string: String = "",
    ) : AbstractLibraryResolutionInfo("default") {
        override val args: List<Variable> = listOf()
    }

    protected abstract val args: List<Variable>
    protected open val valueKey: String
        get() = args.joinToString { it.value }

    override val key: String by lazy { "${typeKey}_${replaceForbiddenChars(valueKey)}" }

    override fun hashCode(): Int = key.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AbstractLibraryResolutionInfo

        return valueKey == other.valueKey
    }

    override fun toString(): String =
        typeKey +
            when {
                args.isEmpty() -> ""
                args.size == 1 -> "[${args[0].value}]"
                else -> args.joinToString(", ", "[", "]") { "${it.name}=${it.value}" }
            }

    companion object {
        fun replaceForbiddenChars(string: String): String = string.replace("""[<>/\\:"|?*]""".toRegex(), "_")
    }
}
