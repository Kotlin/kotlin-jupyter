package org.jetbrains.kotlin.jupyter.test

import jupyter.kotlin.DependsOn
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
