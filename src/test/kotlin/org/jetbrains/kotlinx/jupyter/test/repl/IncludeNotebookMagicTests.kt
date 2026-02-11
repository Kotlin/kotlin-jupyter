package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException
import org.jetbrains.kotlinx.jupyter.test.renderedValue
import org.jetbrains.kotlinx.jupyter.test.testDataDir
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Integration tests for the %includeNotebook magic command.
 * Tests that notebooks can be included and their cells executed as hidden code.
 */
class IncludeNotebookMagicTests : AbstractSingleReplTest() {
    override val repl = makeSimpleRepl()

    @Test
    fun `includeNotebook should execute cells from another notebook`() {
        val notebookPath = testDataDir.resolve("simple-notebook.ipynb")

        // Include the notebook
        evalSuccess("%includeNotebook ${notebookPath.absolutePath}")

        // Verify that the greeting variable is accessible
        val greetingResult = eval("greeting")
        greetingResult.renderedValue shouldBe "Hello"

        // Verify that the sayHello function is accessible
        val result = eval("sayHello(\"World\")")
        result.renderedValue shouldBe "Hello, World!"
    }

    @Test
    fun `includeNotebook should make variables available in current context`() {
        val notebookPath = testDataDir.resolve("notebook-with-variables.ipynb")

        // Include the notebook
        evalSuccess("%includeNotebook ${notebookPath.absolutePath}")

        // Verify that variables are accessible
        val xResult = eval("x")
        xResult.renderedValue shouldBe 42

        val yResult = eval("y")
        yResult.renderedValue shouldBe 84

        // Verify that the data class is accessible
        val personResult = eval("Person(\"Alice\", 30)")
        personResult.renderedValue.toString() shouldContain "Alice"
        personResult.renderedValue.toString() shouldContain "30"
    }

    @Test
    fun `includeNotebook should execute cells in order`() {
        val notebookPath = testDataDir.resolve("notebook-with-sequence.ipynb")

        // Include the notebook
        evalSuccess("%includeNotebook ${notebookPath.absolutePath}")

        // Verify that dependent calculations worked correctly
        val aResult = eval("a")
        aResult.renderedValue shouldBe 1

        val bResult = eval("b")
        bResult.renderedValue shouldBe 2

        val cResult = eval("c")
        cResult.renderedValue shouldBe 3
    }

    @Test
    fun `includeNotebook should throw error for missing file`() {
        val nonExistentPath = testDataDir.resolve("non-existent-notebook.ipynb")

        val exception = evalError<ReplPreprocessingException>(
            "%includeNotebook ${nonExistentPath.absolutePath}",
        )

        exception.message shouldContain "not found"
    }

    @Test
    fun `includeNotebook should handle invalid notebook format gracefully`() {
        val invalidNotebookPath = testDataDir.resolve("invalid-notebook.ipynb")

        val exception = evalError<ReplPreprocessingException>(
            "%includeNotebook ${invalidNotebookPath.absolutePath}",
        )

        exception.message shouldContain "Failed to include notebook"
    }

    @Test
    fun `includeNotebook should resolve relative paths`(
        @TempDir tempDir: Path,
    ) {
        // Create a temporary notebook in the temp directory
        val notebookPath = tempDir.resolve("temp-notebook.ipynb")
        notebookPath.writeText(
            """
            {
              "cells": [
                {
                  "cell_type": "code",
                  "execution_count": 1,
                  "metadata": {},
                  "outputs": [],
                  "source": "val tempValue = 123"
                }
              ],
              "metadata": {
                "kernelspec": {
                  "display_name": "Kotlin",
                  "language": "kotlin",
                  "name": "kotlin"
                }
              },
              "nbformat": 4,
              "nbformat_minor": 2
            }
            """.trimIndent(),
        )

        // Note: The REPL's notebook.workingDir is set to "." by default in tests,
        // so we need to use absolute path for this test case
        evalSuccess("%includeNotebook ${notebookPath.toAbsolutePath()}")

        // Verify the variable from the included notebook is accessible
        val result = eval("tempValue")
        result.renderedValue shouldBe 123
    }

    @Test
    fun `includeNotebook can be called multiple times`() {
        // Create two simple notebooks with different variables
        val notebook1 = testDataDir.resolve("simple-notebook.ipynb")
        val notebook2 = testDataDir.resolve("notebook-with-variables.ipynb")

        // Include both notebooks
        evalSuccess("%includeNotebook ${notebook1.absolutePath}")
        evalSuccess("%includeNotebook ${notebook2.absolutePath}")

        // Verify variables from both notebooks are accessible
        val greetingResult = eval("greeting")
        greetingResult.renderedValue shouldBe "Hello"

        val xResult = eval("x")
        xResult.renderedValue shouldBe 42
    }

    @Test
    fun `includeNotebook variables persist across cells`() {
        val notebookPath = testDataDir.resolve("simple-notebook.ipynb")

        // Include the notebook
        evalSuccess("%includeNotebook ${notebookPath.absolutePath}")

        // Execute additional code that uses included variables
        val result1 = eval("val modifiedGreeting = greeting + \" there\"")
        val result2 = eval("modifiedGreeting")
        result2.renderedValue shouldBe "Hello there"

        // Use the included function in new code
        val result3 = eval("sayHello(\"Kotlin\").uppercase()")
        result3.renderedValue shouldBe "HELLO, KOTLIN!"
    }

    @Test
    fun `includeNotebook should respect tryIgnoreErrors flag`() {
        val nonExistentPath = testDataDir.resolve("non-existent-notebook.ipynb")

        // Using -? prefix should not throw an exception
        evalSuccess("%includeNotebook ${nonExistentPath.absolutePath}")

        // Verify that the REPL is still functional
        val result = eval("1 + 1")
        result.renderedValue shouldBe 2
    }

    @Test
    fun `includeNotebook with empty notebook should not fail`(
        @TempDir tempDir: Path,
    ) {
        // Create an empty notebook
        val emptyNotebookPath = tempDir.resolve("empty-notebook.ipynb")
        emptyNotebookPath.writeText(
            """
            {
              "cells": [],
              "metadata": {
                "kernelspec": {
                  "display_name": "Kotlin",
                  "language": "kotlin",
                  "name": "kotlin"
                }
              },
              "nbformat": 4,
              "nbformat_minor": 2
            }
            """.trimIndent(),
        )

        // Should not throw an exception
        evalSuccess("%includeNotebook ${emptyNotebookPath.toAbsolutePath()}")

        // Verify REPL is still functional
        val result = eval("42")
        result.renderedValue shouldBe 42
    }

    @Test
    fun `includeNotebook with markdown cells should skip them`(
        @TempDir tempDir: Path,
    ) {
        // Create a notebook with mixed markdown and code cells
        val mixedNotebookPath = tempDir.resolve("mixed-notebook.ipynb")
        mixedNotebookPath.writeText(
            """
            {
              "cells": [
                {
                  "cell_type": "markdown",
                  "metadata": {},
                  "source": "# This is markdown"
                },
                {
                  "cell_type": "code",
                  "execution_count": 1,
                  "metadata": {},
                  "outputs": [],
                  "source": "val mixedValue = 999"
                },
                {
                  "cell_type": "markdown",
                  "metadata": {},
                  "source": "More markdown"
                }
              ],
              "metadata": {
                "kernelspec": {
                  "display_name": "Kotlin",
                  "language": "kotlin",
                  "name": "kotlin"
                }
              },
              "nbformat": 4,
              "nbformat_minor": 2
            }
            """.trimIndent(),
        )

        // Include should only execute code cells
        evalSuccess("%includeNotebook ${mixedNotebookPath.toAbsolutePath()}")

        // Verify the code cell was executed
        val result = eval("mixedValue")
        result.renderedValue shouldBe 999
    }
}
