package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.repl.impl.ReplForJupyterImpl

class ReplFactoryBase(
    private val componentsProvider: ReplComponentsProvider,
) : ReplFactory {
    override fun createRepl(): ReplForJupyter =
        with(componentsProvider) {
            ReplForJupyterImpl(
                loggerFactory,
                resolutionInfoProvider,
                displayHandler,
                scriptClasspath,
                homeDir,
                mavenRepositories,
                libraryResolver,
                runtimeProperties,
                scriptReceivers,
                kernelRunMode,
                notebook,
                librariesScanner,
                debugPort,
                commHandlers,
                httpClient,
                libraryDescriptorsManager,
                libraryReferenceParser,
                librariesProcessor,
                replOptions,
                sessionOptions,
                loggingManager,
                magicsHandler,
                inMemoryReplResultsHolder,
                replCompilerMode,
                extraCompilerArguments,
            )
        }
}
