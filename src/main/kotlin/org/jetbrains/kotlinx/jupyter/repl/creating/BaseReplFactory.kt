package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.MutableNotebook
import org.jetbrains.kotlinx.jupyter.NotebookImpl
import org.jetbrains.kotlinx.jupyter.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesScanner
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.messaging.DisplayHandler
import org.jetbrains.kotlinx.jupyter.messaging.NoOpDisplayHandler
import java.io.File
import kotlin.script.experimental.dependencies.RepositoryCoordinates

open class BaseReplFactory : ReplFactory() {
    override fun provideResolutionInfoProvider(): ResolutionInfoProvider = EmptyResolutionInfoProvider
    override fun provideDisplayHandler(): DisplayHandler = NoOpDisplayHandler
    override fun provideNotebook(): MutableNotebook = NotebookImpl(runtimeProperties)
    override fun provideScriptClasspath() = emptyList<File>()
    override fun provideHomeDir(): File? = null
    override fun provideMavenRepositories() = emptyList<RepositoryCoordinates>()
    override fun provideLibraryResolver(): LibraryResolver? = null
    override fun provideRuntimeProperties(): ReplRuntimeProperties = defaultRuntimeProperties
    override fun provideScriptReceivers() = emptyList<Any>()
    override fun provideIsEmbedded() = false
    override fun provideLibrariesScanner(): LibrariesScanner = LibrariesScanner(notebook)
}
