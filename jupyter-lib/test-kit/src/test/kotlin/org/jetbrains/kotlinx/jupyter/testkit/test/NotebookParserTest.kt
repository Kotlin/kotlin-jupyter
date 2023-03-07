package org.jetbrains.kotlinx.jupyter.testkit.test

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.jupyter.parser.JupyterParser
import org.jetbrains.jupyter.parser.notebook.CodeCell
import org.jetbrains.jupyter.parser.notebook.ExecuteResult
import org.jetbrains.kotlinx.jupyter.api.MimeTypes
import org.jetbrains.kotlinx.jupyter.config.notebookKernelSpec
import org.jetbrains.kotlinx.jupyter.config.notebookLanguageInfo
import org.junit.jupiter.api.Test
import java.io.File

class NotebookParserTest {

    @Test
    fun `parse simple notebook`() {
        val notebook = JupyterParser.parse(testData.resolve("testNotebook1.ipynb"))

        with(notebook.metadata.languageInfo) {
            shouldNotBeNull()
            name shouldBe notebookLanguageInfo.name
            fileExtension shouldBe notebookLanguageInfo.fileExtension
            mimetype shouldBe notebookLanguageInfo.mimetype
            pygmentsLexer shouldBe notebookLanguageInfo.pygmentsLexer
        }

        with(notebook.metadata.kernelSpec) {
            shouldNotBeNull()
            name shouldBe notebookKernelSpec.name
            displayName shouldBe notebookKernelSpec.displayName
        }

        val cells = notebook.cells
        cells shouldHaveSize 3

        with(cells[0]) {
            shouldBeInstanceOf<CodeCell>()
            source shouldBe "2 + 2"
            val out = outputs.single()
            out.shouldBeInstanceOf<ExecuteResult>()
            out.executionCount shouldBe 1
            out.data[MimeTypes.PLAIN_TEXT] shouldBe "4"
        }

        with(cells[1]) {
            shouldBeInstanceOf<CodeCell>()
            source shouldContain "<img src="
            val out = outputs.single()
            out.shouldBeInstanceOf<ExecuteResult>()
            out.executionCount shouldBe 2
            out.data[MimeTypes.HTML] shouldContain "<img src="
        }

        with(cells[2]) {
            source.shouldBeEmpty()
        }
    }

    companion object {
        val testData = File("src/test/testData")
    }
}
