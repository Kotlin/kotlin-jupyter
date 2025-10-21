package org.jetbrains.kotlinx.jupyter.test

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.api.dependencies.RepositoryDescription
import org.jetbrains.kotlinx.jupyter.dependencies.AmperMavenDependenciesResolver
import org.jetbrains.kotlinx.jupyter.dependencies.SourceAwareDependenciesResolver
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.dependencies.ArtifactWithLocation
import kotlin.test.assertTrue

class ResolverTests {
    private val logger = testLoggerFactory.getLogger(ResolverTests::class.java)

    private fun SourceAwareDependenciesResolver.doResolve(artifact: String): List<File> {
        testRepositories.forEach {
            addRepository(RepositoryDescription(it.coordinates))
        }
        assertTrue(acceptsArtifact(artifact))
        val result =
            runBlocking {
                resolve(
                    listOf(
                        ArtifactWithLocation(artifact, null),
                    ),
                    resolveSources = false,
                )
            }
        assertTrue(result is ResultWithDiagnostics.Success)
        return result.value.binaries
    }

    @Test
    fun resolveSparkMlLibTest() {
        val files = AmperMavenDependenciesResolver().doResolve("org.apache.spark:spark-mllib_2.11:2.4.4")
        logger.debug("Downloaded files: ${files.count()}")
        files.forEach {
            logger.debug(it.toString())
        }
    }
}
