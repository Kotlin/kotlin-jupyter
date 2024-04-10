package org.jetbrains.kotlinx.jupyter.testkit

import jupyter.kotlin.DependsOn
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.config.defaultRepositoriesCoordinates
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.createLibraryHttpUtil
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.repl.creating.createRepl
import org.jetbrains.kotlinx.jupyter.repl.embedded.NoOpInMemoryReplResultsHolder
import java.io.File

fun interface ReplProvider {
    operator fun invoke(classpath: List<File>): ReplForJupyter

    companion object {
        private val httpUtil = createLibraryHttpUtil(DefaultKernelLoggerFactory)

        val withoutLibraryResolution =
            ReplProvider { classpath ->
                createRepl(httpUtil, scriptClasspath = classpath, isEmbedded = true).apply {
                    initializeWithCurrentClasspath()
                }
            }

        fun withDefaultClasspathResolution(
            shouldResolve: (String?) -> Boolean = { true },
            shouldResolveToEmpty: (String?) -> Boolean = { false },
        ) = ReplProvider { classpath ->
            val resolver =
                run {
                    var res: LibraryResolver = ClasspathLibraryResolver(httpUtil.libraryDescriptorsManager, null, shouldResolve)
                    res = ToEmptyLibraryResolver(res, shouldResolveToEmpty)
                    res
                }

            createRepl(
                httpUtil = httpUtil,
                scriptClasspath = classpath,
                isEmbedded = true,
                mavenRepositories = defaultRepositoriesCoordinates,
                libraryResolver = resolver,
                inMemoryReplResultsHolder = NoOpInMemoryReplResultsHolder,
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
