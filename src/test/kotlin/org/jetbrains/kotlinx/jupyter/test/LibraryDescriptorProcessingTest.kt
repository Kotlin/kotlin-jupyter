package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryDescriptor
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryName
import org.junit.jupiter.api.Test

class LibraryDescriptorProcessingTest {

    @Test
    fun `ordered descriptor properties`() =
        doTest(
            "lib(42, abc, y=8)",
            """
                {
                    "properties": [
                        {"name": "a", "value": "def1"},
                        {"name": "b", "value": "def2"},
                        {"name": "x", "value": "def3"},
                        {"name": "y", "value": "def4"}
                    ],
                    "init": [
                        "val a = ${'$'}a",
                        "val b = ${'$'}b",
                        "val x = ${'$'}x",
                        "val y = ${'$'}y"
                    ]
                }
            """.trimIndent(),
        ) { definition ->
            definition.init.map { (it as CodeExecutionCallback).code } shouldBe listOf(
                "val a = 42",
                "val b = abc",
                "val x = def3",
                "val y = 8",
            )
        }

    private fun doTest(
        libraryCall: String,
        descriptorString: String,
        definitionChecker: (LibraryDefinition) -> Unit,
    ) {
        val (_, arguments) = parseLibraryName(libraryCall)
        val descriptor = parseLibraryDescriptor(descriptorString)
        val definition = descriptor.convertToDefinition(arguments)
        definitionChecker(definition)
    }
}
