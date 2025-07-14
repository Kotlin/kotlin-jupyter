package org.jetbrains.kotlinx.jupyter.test.repl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.api.EmbeddedKernelRunMode
import org.jetbrains.kotlinx.jupyter.api.StandaloneKernelRunMode
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import org.jetbrains.kotlinx.jupyter.api.outputs.DisplayHandler
import org.jetbrains.kotlinx.jupyter.libraries.DefaultResolutionInfoProviderFactory
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.createLibraryHttpUtil
import org.jetbrains.kotlinx.jupyter.libraries.getStandardResolver
import org.jetbrains.kotlinx.jupyter.messaging.CommunicationFacilityMock
import org.jetbrains.kotlinx.jupyter.messaging.NoOpDisplayHandler
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.repl.creating.ReplComponentsProviderBase
import org.jetbrains.kotlinx.jupyter.repl.creating.createRepl
import org.jetbrains.kotlinx.jupyter.repl.embedded.DefaultInMemoryReplResultsHolder
import org.jetbrains.kotlinx.jupyter.repl.impl.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.repl.notebook.MutableNotebook
import org.jetbrains.kotlinx.jupyter.test.assertSuccess
import org.jetbrains.kotlinx.jupyter.test.classPathEntry
import org.jetbrains.kotlinx.jupyter.test.classpath
import org.jetbrains.kotlinx.jupyter.test.evalEx
import org.jetbrains.kotlinx.jupyter.test.standardResolverRuntimeProperties
import org.jetbrains.kotlinx.jupyter.test.testLibraryResolver
import org.jetbrains.kotlinx.jupyter.test.testLoggerFactory
import org.jetbrains.kotlinx.jupyter.test.testRepositories
import org.jetbrains.kotlinx.jupyter.test.toLibraries
import java.io.File

abstract class AbstractReplTest {
    protected val httpUtil = createLibraryHttpUtil(testLoggerFactory)

    private val classpathWithTestLib =
        buildList {
            addAll(classpath)
            add(classPathEntry<AbstractReplTest>())
            add(classPathEntry<ReplForJupyterImpl>())
        }

    fun ReplForJupyter.listErrorsBlocking(code: String): ListErrorsResult =
        runBlocking {
            var res: ListErrorsResult? = null
            listErrors(code) {
                res = it
            }
            res!!
        }

    fun ReplForJupyter.completeBlocking(
        code: String,
        cursor: Int,
    ): CompletionResult =
        runBlocking {
            var res: CompletionResult? = null
            complete(code, cursor) {
                res = it
            }
            res!!
        }

    protected fun makeSimpleRepl(): ReplForJupyter = createRepl(httpUtil, scriptClasspath = classpath)

    protected fun makeReplWithTestResolver(displayHandler: DisplayHandler = NoOpDisplayHandler): ReplForJupyter =
        createRepl(
            httpUtil,
            scriptClasspath = classpath,
            homeDir = homeDir,
            libraryResolver = testLibraryResolver,
            displayHandler = displayHandler,
        )

    protected fun makeReplWithStandardResolver(
        displayHandlerProvider: (MutableNotebook) -> DisplayHandler = { NoOpDisplayHandler },
    ): ReplForJupyter {
        val standardResolutionInfoProvider =
            DefaultResolutionInfoProviderFactory.create(
                httpUtil,
                testLoggerFactory,
            )
        val resolver =
            getStandardResolver(
                standardResolutionInfoProvider,
                httpUtil.httpClient,
                httpUtil.libraryDescriptorsManager,
            )
        val myHomeDir = homeDir
        val factory =
            object : ReplComponentsProviderBase() {
                override fun provideResolutionInfoProvider() = standardResolutionInfoProvider

                override fun provideScriptClasspath() = classpath

                override fun provideHomeDir() = myHomeDir

                override fun provideMavenRepositories() = testRepositories

                override fun provideLibraryResolver() = resolver

                override fun provideRuntimeProperties() = standardResolverRuntimeProperties

                override fun provideScriptReceivers() = emptyList<Any>()

                override fun provideKernelRunMode() = StandaloneKernelRunMode

                override fun provideDisplayHandler() = displayHandlerProvider(notebook)

                override fun provideCommunicationFacility() = CommunicationFacilityMock

                override fun provideDebugPort(): Int? = null
            }
        return factory.createRepl()
    }

    protected fun makeEmbeddedRepl(displayHandler: DisplayHandler = NoOpDisplayHandler): ReplForJupyter {
        val embeddedClasspath: List<File> = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
        val standardResolutionInfoProvider =
            DefaultResolutionInfoProviderFactory.create(
                httpUtil,
                testLoggerFactory,
            )
        val libraryResolver =
            getStandardResolver(
                standardResolutionInfoProvider,
                httpUtil.httpClient,
                httpUtil.libraryDescriptorsManager,
            )
        return createRepl(
            httpUtil,
            scriptClasspath = embeddedClasspath,
            kernelRunMode = EmbeddedKernelRunMode,
            displayHandler = displayHandler,
            inMemoryReplResultsHolder = DefaultInMemoryReplResultsHolder(),
            libraryResolver = libraryResolver,
        )
    }

    protected fun makeReplWithLibraries(vararg libs: Pair<String, LibraryDefinition>) = makeReplWithLibraries(libs.toList().toLibraries())

    protected fun makeReplWithLibraries(libraryResolver: LibraryResolver) =
        createRepl(
            httpUtil,
            scriptClasspath = classpathWithTestLib,
            homeDir = homeDir,
            mavenRepositories = testRepositories,
            libraryResolver = libraryResolver,
        )

    protected fun makeReplEnablingSingleLibrary(
        definition: LibraryDefinition,
        args: List<Variable> = emptyList(),
    ): ReplForJupyter {
        val repl = makeReplWithLibraries("mylib" to definition)
        val paramList =
            if (args.isEmpty()) {
                ""
            } else {
                args.joinToString(", ", "(", ")") { "${it.name}=${it.value}" }
            }
        val result = repl.evalEx("%use mylib$paramList")
        result.assertSuccess()
        return repl
    }

    companion object {
        @JvmStatic
        protected val homeDir = File("")
    }
}
