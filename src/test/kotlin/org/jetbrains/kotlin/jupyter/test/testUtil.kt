package org.jetbrains.kotlin.jupyter.test

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import jupyter.kotlin.DependsOn
import kotlinx.coroutines.Deferred
import org.jetbrains.kotlin.jupyter.LibraryDescriptor
import org.jetbrains.kotlin.jupyter.ResolverConfig
import org.jetbrains.kotlin.jupyter.asAsync
import org.jetbrains.kotlin.jupyter.defaultRepositories
import org.jetbrains.kotlin.jupyter.parserLibraryDescriptors
import org.jetbrains.kotlin.jupyter.readLibraries
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext

val classpath = scriptCompilationClasspathFromContext(
        "jupyter-lib",
        "kotlin-stdlib",
        "kotlin-reflect",
        "kotlin-script-runtime",
        classLoader = DependsOn::class.java.classLoader
)

val testResolverConfig = ResolverConfig(defaultRepositories,
        parserLibraryDescriptors(readLibraries().toMap()).asAsync())

fun Collection<Pair<String, String>>.toLibrariesAsync(): Deferred<Map<String, LibraryDescriptor>> {
    val parser = Parser.default()
    val libJsons = map { it.first to parser.parse(StringBuilder(it.second)) as JsonObject }.toMap()
    return parserLibraryDescriptors(libJsons).asAsync()
}
