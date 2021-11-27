package org.jetbrains.kotlinx.jupyter.testkit

import jupyter.kotlin.DependsOn
import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.defaultRepositories
import org.jetbrains.kotlinx.jupyter.dependencies.ResolverConfig
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import java.io.File

fun interface ReplProvider {
    operator fun invoke(classpath: List<File>): ReplForJupyter

    companion object {
        val withoutLibraryResolution = ReplProvider { classpath ->
            ReplForJupyterImpl(EmptyResolutionInfoProvider, classpath, isEmbedded = true).apply {
                initializeWithCurrentClasspath()
            }
        }

        fun withDefaultClasspathResolution(
            shouldResolve: (String?) -> Boolean = { true }
        ) = ReplProvider { classpath ->
            ReplForJupyterImpl(
                EmptyResolutionInfoProvider,
                classpath,
                isEmbedded = true,
                resolverConfig = ResolverConfig(defaultRepositories, ClasspathLibraryResolver(null, shouldResolve))
            ).apply {
                initializeWithCurrentClasspath()
            }
        }

        private fun ReplForJupyterImpl.initializeWithCurrentClasspath() {
            eval { librariesScanner.addLibrariesFromClassLoader(currentClassLoader, this) }
        }

        private val currentClassLoader = DependsOn::class.java.classLoader
    }
}
