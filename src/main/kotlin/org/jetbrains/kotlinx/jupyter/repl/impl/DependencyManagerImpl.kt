package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlinx.jupyter.api.dependencies.DependencyManager
import org.jetbrains.kotlinx.jupyter.dependencies.JupyterScriptDependenciesResolver
import org.jetbrains.kotlinx.jupyter.dependencies.JupyterScriptDependenciesResolverImpl
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.repl.MavenRepositoryCoordinates
import org.jetbrains.kotlinx.jupyter.repl.ReplOptions
import java.io.File
import kotlin.reflect.KMutableProperty0

class DependencyManagerImpl(
    loggerFactory: KernelLoggerFactory,
    mavenRepositories: List<MavenRepositoryCoordinates>,
    resolveSourcesOption: KMutableProperty0<Boolean>,
    private val trackClasspath: KMutableProperty0<Boolean>,
) : DependencyManager {
    private val binaries = mutableSetOf<File>()
    private val addedBinaries = mutableListOf<File>()
    private val sources = mutableSetOf<File>()
    private val addedSources = mutableListOf<File>()

    override val resolver: JupyterScriptDependenciesResolver =
        JupyterScriptDependenciesResolverImpl(
            loggerFactory,
            mavenRepositories,
            resolveSourcesOption,
            addedBinaries,
            addedSources,
        )

    override val currentBinaryClasspath: List<File>
        get() = binaries.toList()

    override val currentSourcesClasspath: List<File>
        get() = sources.toList()

    override fun addBinaryClasspath(newClasspath: Collection<File>) {
        addedBinaries.addAll(newClasspath)
    }

    override fun addSourceClasspath(newClasspath: Collection<File>) {
        addedSources.addAll(newClasspath)
    }

    val recentlyAddedBinaryClasspath: List<File> get() = addedBinaries.distinct()

    /**
     * Updates current classpath with newly resolved libraries paths
     * Also, prints information about resolved libraries to stdout if [ReplOptions.trackClasspath] is true
     *
     * @return Newly resolved classpath
     */
    fun popAddedClasspath(): List<File> {
        val resolvedClasspath = recentlyAddedBinaryClasspath
        addedBinaries.clear()
        val (oldClasspath, newClasspath) = resolvedClasspath.partition { it in binaries }
        binaries.addAll(newClasspath)
        if (trackClasspath.get()) {
            val sb = StringBuilder()
            if (newClasspath.isNotEmpty()) {
                sb.appendLine("${newClasspath.count()} new paths were added to classpath:")
                newClasspath.sortedBy { it }.forEach { sb.appendLine(it) }
            }
            if (oldClasspath.isNotEmpty()) {
                sb.appendLine("${oldClasspath.count()} resolved paths were already in classpath:")
                oldClasspath.sortedBy { it }.forEach { sb.appendLine(it) }
            }
            sb.appendLine("Current classpath size: ${binaries.count()}")
            println(sb.toString())
        }

        return newClasspath
    }

    fun popAddedSources(): List<File> {
        val result = addedSources.filter { it !in sources }.distinct()
        addedSources.clear()
        sources.addAll(result)
        return result
    }
}
