package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.repl.impl.ReplForJupyterImpl

class ReplFactoryBase(
    private val componentsProvider: ReplComponentsProvider,
) : ReplFactory {
    override fun createRepl(): ReplForJupyter {
        return with(componentsProvider) {
            ReplForJupyterImpl(
                resolutionInfoProvider,
                displayHandler,
                scriptClasspath,
                homeDir,
                mavenRepositories,
                libraryResolver,
                runtimeProperties,
                scriptReceivers,
                isEmbedded,
                notebook,
                librariesScanner,
                debugPort,
                commHandlers,
            )
        }
    }
}
