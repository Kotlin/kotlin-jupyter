package org.jetbrains.kotlinx.jupyter.test.repl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.MutableNotebook
import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.getDefaultClasspathResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.getStandardResolver
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.NoOpDisplayHandler
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlinx.jupyter.repl.creating.BaseReplFactory
import org.jetbrains.kotlinx.jupyter.repl.creating.MockJupyterConnection
import org.jetbrains.kotlinx.jupyter.repl.creating.createRepl
import org.jetbrains.kotlinx.jupyter.test.classPathEntry
import org.jetbrains.kotlinx.jupyter.test.classpath
import org.jetbrains.kotlinx.jupyter.test.evalRaw
import org.jetbrains.kotlinx.jupyter.test.standardResolverRuntimeProperties
import org.jetbrains.kotlinx.jupyter.test.testLibraryResolver
import org.jetbrains.kotlinx.jupyter.test.testRepositories
import org.jetbrains.kotlinx.jupyter.test.toLibraries
import java.io.File

abstract class AbstractReplTest {
    val classpathWithTestLib = buildList {
        addAll(classpath)
        add(classPathEntry<AbstractReplTest>())
        add(classPathEntry<ReplForJupyterImpl>())
    }

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

    protected fun makeReplWithStandardResolver(
        displayHandlerProvider: (MutableNotebook) -> DisplayHandler = { NoOpDisplayHandler },
    ): ReplForJupyter {
        val standardResolutionInfoProvider = getDefaultClasspathResolutionInfoProvider()
        val resolver = getStandardResolver(".", standardResolutionInfoProvider)
        val myHomeDir = homeDir
        val factory = object : BaseReplFactory() {
            override fun provideResolutionInfoProvider() = standardResolutionInfoProvider
            override fun provideScriptClasspath() = classpath
            override fun provideHomeDir() = myHomeDir
            override fun provideMavenRepositories() = testRepositories
            override fun provideLibraryResolver() = resolver
            override fun provideRuntimeProperties() = standardResolverRuntimeProperties
            override fun provideScriptReceivers() = emptyList<Any>()
            override fun provideIsEmbedded() = false
            override fun provideDisplayHandler() = displayHandlerProvider(notebook)
            override fun provideConnection() = MockJupyterConnection
            override fun provideDebugPort(): Int? = null
        }
        return factory.createRepl()
    }

    protected fun makeEmbeddedRepl(displayHandler: DisplayHandler = NoOpDisplayHandler): ReplForJupyter {
        val embeddedClasspath: List<File> = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
        return createRepl(resolutionInfoProvider, embeddedClasspath, isEmbedded = true, displayHandler = displayHandler)
    }

    protected fun makeReplWithLibraries(vararg libs: Pair<String, LibraryDefinition>) = makeReplWithLibraries(libs.toList().toLibraries())

    protected fun makeReplWithLibraries(libraryResolver: LibraryResolver) = createRepl(
        resolutionInfoProvider,
        classpathWithTestLib,
        homeDir,
        testRepositories,
        libraryResolver,
    )

    protected fun makeReplEnablingSingleLibrary(definition: LibraryDefinition, args: List<Variable> = emptyList()): ReplForJupyter {
        val repl = makeReplWithLibraries("mylib" to definition)
        val paramList = if (args.isEmpty()) ""
        else args.joinToString(", ", "(", ")") { "${it.name}=${it.value}" }
        repl.evalRaw("%use mylib$paramList")
        return repl
    }

    companion object {
        @JvmStatic
        val resolutionInfoProvider = EmptyResolutionInfoProvider

        @JvmStatic
        protected val homeDir = File("")
    }
}
