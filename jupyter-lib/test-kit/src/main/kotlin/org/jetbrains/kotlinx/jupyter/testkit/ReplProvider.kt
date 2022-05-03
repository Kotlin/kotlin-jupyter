package org.jetbrains.kotlinx.jupyter.testkit

import jupyter.kotlin.DependsOn
import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.defaultRepositories
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
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
            shouldResolve: (String?) -> Boolean = { true },
            shouldResolveToEmpty: (String?) -> Boolean = { false },
        ) = ReplProvider { classpath ->
            val resolver = run {
                var res: LibraryResolver = ClasspathLibraryResolver(null, shouldResolve)
                res = ToEmptyLibraryResolver(res, shouldResolveToEmpty)
                res
            }

            ReplForJupyterImpl(
                EmptyResolutionInfoProvider,
                classpath,
                isEmbedded = true,
                mavenRepositories = defaultRepositories,
                libraryResolver = resolver
            ).apply {
                initializeWithCurrentClasspath()
            }
        }

        fun forLibrariesTesting(libraries: Collection<String>): ReplProvider {
            return withDefaultClasspathResolution(
                shouldResolveToEmpty = { it in libraries }
            )
        }

        private fun ReplForJupyterImpl.initializeWithCurrentClasspath() {
            eval { librariesScanner.addLibrariesFromClassLoader(currentClassLoader, this) }
        }

        private val currentClassLoader = DependsOn::class.java.classLoader
    }
}
