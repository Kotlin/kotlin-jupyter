package org.jetbrains.kotlinx.jupyter.test.repl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.dependencies.ResolverConfig
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.KERNEL_LIBRARIES
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.getStandardResolver
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlinx.jupyter.test.classpath
import org.jetbrains.kotlinx.jupyter.test.standardResolverRuntimeProperties
import org.jetbrains.kotlinx.jupyter.test.testRepositories
import org.jetbrains.kotlinx.jupyter.test.testResolverConfig
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
        return ReplForJupyterImpl(resolutionInfoProvider, classpath)
    }

    protected fun makeReplWithTestResolver(): ReplForJupyter {
        return ReplForJupyterImpl(resolutionInfoProvider, classpath, homeDir, testResolverConfig)
    }

    protected fun makeReplWithStandardResolver(): ReplForJupyter {
        val standardResolutionInfoProvider = ResolutionInfoProvider.withDefaultDirectoryResolution(KERNEL_LIBRARIES.homeLibrariesDir(homeDir))
        val config = ResolverConfig(testRepositories, getStandardResolver(".", standardResolutionInfoProvider))
        return ReplForJupyterImpl(standardResolutionInfoProvider, classpath, homeDir, config, standardResolverRuntimeProperties)
    }

    protected fun makeEmbeddedRepl(): ReplForJupyter {
        val embeddedClasspath: List<File> = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
        return ReplForJupyterImpl(resolutionInfoProvider, embeddedClasspath, isEmbedded = true)
    }

    companion object {
        @JvmStatic
        val resolutionInfoProvider = EmptyResolutionInfoProvider

        @JvmStatic
        protected val homeDir = File("")
    }
}
