package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.config.defaultRepositoriesCoordinates
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolver
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.getStandardResolver
import java.io.File

data class ReplConfig(
    val mavenRepositories: List<MavenRepositoryCoordinates> = listOf(),
    val libraryResolver: LibraryResolver? = null,
    val resolutionInfoProvider: ResolutionInfoProvider,
    val embedded: Boolean = false,
) {
    companion object {
        fun create(
            resolutionInfoProvider: ResolutionInfoProvider,
            homeDir: File? = null,
            embedded: Boolean = false,
        ): ReplConfig {
            return ReplConfig(
                mavenRepositories = defaultRepositoriesCoordinates,
                libraryResolver = getStandardResolver(homeDir?.toString(), resolutionInfoProvider),
                resolutionInfoProvider = resolutionInfoProvider,
                embedded = embedded,
            )
        }
    }
}
