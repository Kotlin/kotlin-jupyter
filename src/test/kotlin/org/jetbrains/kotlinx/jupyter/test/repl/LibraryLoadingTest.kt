package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.test.evalRaw
import org.jetbrains.kotlinx.jupyter.test.integrations.MultiConstructor1
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.URLClassLoader
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText

class LibraryLoadingTest : AbstractSingleReplTest() {
    override val repl = makeSimpleRepl()

    private val tempDir = createTempDirectory(this::class.simpleName)

    private inline fun <reified T> loadIntegration() {
        val fqn = T::class.qualifiedName!!
        val json = """
            {
              "definitions": [],
              "producers": [
                {
                  "fqn": "$fqn"
                }
              ]
            }
        """.trimIndent()

        val integrationTempDir = createTempDirectory(tempDir, "jupyterTests_$fqn")
        val jsonDir = integrationTempDir.resolve("META-INF").resolve("kotlin-jupyter-libraries")
        jsonDir.toFile().mkdirs()
        val jsonPath = jsonDir.resolve("libraries.json")
        jsonPath.writeText(json)

        val classLoader = URLClassLoader(
            arrayOf(
                integrationTempDir.toUri().toURL(),
            ),
        )

        repl.eval {
            repl.librariesScanner.addLibrariesFromClassLoader(
                classLoader,
                this,
                repl.notebook,
            )
        }
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterEach
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `check that multi-constructor library is loaded correctly`() {
        loadIntegration<MultiConstructor1>()
        val result = repl.evalRaw("multiConstructor1")
        result shouldBe "lib-2"
    }

    @Test
    fun `descriptor could be loaded by its text`() {
        val tripleQuote = "\"\"\""
        repl.evalRaw(
            """
            loadLibraryDescriptor($tripleQuote
                { "init": ["val xyz = 4242"] }
            $tripleQuote)
            """.trimIndent(),
        )
        val result = repl.evalRaw("xyz")
        result shouldBe 4242
    }
}
