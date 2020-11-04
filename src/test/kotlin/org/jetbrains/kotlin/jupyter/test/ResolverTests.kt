package org.jetbrains.kotlin.jupyter.test

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.jupyter.config.defaultRepositories
import org.jetbrains.kotlin.mainKts.impl.IvyResolver
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver
import kotlin.test.assertTrue

class ResolverTests {
    private val log: Logger by lazy { LoggerFactory.getLogger("resolver") }

    private fun ExternalDependenciesResolver.doResolve(artifact: String): List<File> {
        defaultRepositories.forEach { addRepository(it) }
        assertTrue(acceptsArtifact(artifact))
        val result = runBlocking { resolve(artifact) }
        assertTrue(result is ResultWithDiagnostics.Success)
        return result.value
    }

    @Test
    fun resolveSparkMlLibTest() {
        val files = IvyResolver().doResolve("org.apache.spark:spark-mllib_2.11:2.4.4")
        log.debug("Downloaded files: ${files.count()}")
        files.forEach {
            log.debug(it.toString())
        }
    }
}
