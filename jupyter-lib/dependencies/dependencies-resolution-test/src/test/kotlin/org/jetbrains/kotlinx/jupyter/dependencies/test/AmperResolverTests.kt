package org.jetbrains.kotlinx.jupyter.dependencies.test

import io.kotest.inspectors.shouldForAny
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.common.kernelMavenCacheDir
import org.jetbrains.kotlinx.jupyter.dependencies.api.ArtifactRequest
import org.jetbrains.kotlinx.jupyter.dependencies.api.SourceAwareDependenciesResolver
import org.jetbrains.kotlinx.jupyter.dependencies.maven.AmperMavenDependenciesResolver
import org.jetbrains.kotlinx.jupyter.dependencies.maven.repositories.CENTRAL_REPO
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.File

class AmperResolverTests {
    private val logger = LoggerFactory.getLogger(AmperResolverTests::class.java)
    private val resolver = AmperMavenDependenciesResolver(kernelMavenCacheDir.toPath())

    @Test
    fun `resolve standard heavy-weight dependency - SparkMlLib`() {
        val files = resolver.doResolve("org.apache.spark:spark-mllib_2.11:2.4.4")
        files.size shouldBeGreaterThanOrEqual 200
    }

    @Test
    fun `resolve KMP dependency - koog`() {
        val files = resolver.doResolve("ai.koog:koog-agents:0.5.0")
        files.shouldForAny { it.name.startsWith("agents-core-jvm") }
        files.size shouldBeGreaterThanOrEqual 100
    }

    private fun SourceAwareDependenciesResolver.doResolve(artifact: String): List<File> {
        addRepository(CENTRAL_REPO)
        acceptsArtifact(artifact).shouldBeTrue()
        val result =
            runBlocking {
                resolve(
                    listOf(
                        ArtifactRequest(artifact, null),
                    ),
                    resolveSources = false,
                )
            }
        val binaries =
            result
                .shouldBeSuccess()
                .value.binaries

        logger.warn(
            "Downloaded files (${binaries.count()}):\n" +
                binaries.joinToString("\n"),
        )
        return binaries
    }
}
