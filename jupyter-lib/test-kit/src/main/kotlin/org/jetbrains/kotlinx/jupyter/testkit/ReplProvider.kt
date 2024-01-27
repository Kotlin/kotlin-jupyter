package org.jetbrains.kotlinx.jupyter.testkit

import jupyter.kotlin.DependsOn
import org.jetbrains.kotlinx.jupyter.config.defaultRepositoriesCoordinates
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.repl.creating.createRepl
import java.io.File

fun interface ReplProvider {
    operator fun invoke(classpath: List<File>): ReplForJupyter

    companion object {
        val withoutLibraryResolution = ReplProvider { classpath ->
            createRepl(EmptyResolutionInfoProvider, classpath, isEmbedded = true).apply {
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

            createRepl(
                EmptyResolutionInfoProvider,
                classpath,
                isEmbedded = true,
                mavenRepositories = defaultRepositoriesCoordinates,
                libraryResolver = resolver,
            ).apply {
                initializeWithCurrentClasspath()
            }
        }

        @Suppress("unused")
        fun forLibrariesTesting(libraries: Collection<String>): ReplProvider {
            return withDefaultClasspathResolution(
                shouldResolveToEmpty = { it in libraries },
            )
        }

        private fun ReplForJupyter.initializeWithCurrentClasspath() {
            eval { librariesScanner.addLibrariesFromClassLoader(currentClassLoader, this, notebook) }
        }

        @Suppress("unused")
        private val currentClassLoader = DependsOn::class.java.classLoader
    }
}
