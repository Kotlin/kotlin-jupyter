package org.jetbrains.kotlinx.jupyter.test.repl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.KERNEL_LIBRARIES
import org.jetbrains.kotlinx.jupyter.libraries.getDefaultDirectoryResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.getStandardResolver
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.NoOpDisplayHandler
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlinx.jupyter.repl.creating.createRepl
import org.jetbrains.kotlinx.jupyter.test.classpath
import org.jetbrains.kotlinx.jupyter.test.standardResolverRuntimeProperties
import org.jetbrains.kotlinx.jupyter.test.testLibraryResolver
import org.jetbrains.kotlinx.jupyter.test.testRepositories
import java.io.File

abstract class AbstractReplTest {
    val classpathWithTestLib = classpath + File(AbstractReplTest::class.java.protectionDomain.codeSource.location.toURI().path)

    fun ReplForJupyter.listErrorsBlocking(code: String): ListErrorsResult {
        return runBlocking {
            var res: ListErrorsResult? = null
            listErrors(code) {
                res = it
            }
            res!!
        }
    }

    fun ReplForJupyter.completeBlocking(code: String, cursor: Int): CompletionResult {
        return runBlocking {
            var res: CompletionResult? = null
            complete(code, cursor) {
                res = it
            }
            res!!
        }
    }

    protected fun makeSimpleRepl(): ReplForJupyter {
        return createRepl(resolutionInfoProvider, classpath)
    }

    protected fun makeReplWithTestResolver(displayHandler: DisplayHandler = NoOpDisplayHandler): ReplForJupyter {
        return createRepl(resolutionInfoProvider, classpath, homeDir, libraryResolver = testLibraryResolver, displayHandler = displayHandler)
    }

    protected fun makeReplWithStandardResolver(displayHandler: DisplayHandler = NoOpDisplayHandler): ReplForJupyter {
        val standardResolutionInfoProvider = getDefaultDirectoryResolutionInfoProvider(KERNEL_LIBRARIES.homeLibrariesDir(homeDir))
        val resolver = getStandardResolver(".", standardResolutionInfoProvider)
        return createRepl(standardResolutionInfoProvider, classpath, homeDir, testRepositories, resolver, standardResolverRuntimeProperties, displayHandler = displayHandler)
    }

    protected fun makeEmbeddedRepl(displayHandler: DisplayHandler = NoOpDisplayHandler): ReplForJupyter {
        val embeddedClasspath: List<File> = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
        return createRepl(resolutionInfoProvider, embeddedClasspath, isEmbedded = true, displayHandler = displayHandler)
    }

    companion object {
        @JvmStatic
        val resolutionInfoProvider = EmptyResolutionInfoProvider

        @JvmStatic
        protected val homeDir = File("")
    }
}
