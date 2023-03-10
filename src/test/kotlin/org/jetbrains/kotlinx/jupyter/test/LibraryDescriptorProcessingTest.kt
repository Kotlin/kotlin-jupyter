package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecutionCallback
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptor
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

    @Test
    fun `ordered descriptors should remain ordered after serialization`() = doSerializeDeserializeTest(
        """
            {
                "properties": [
                    {"name": "a", "value": "def1"},
                    {"name": "b", "value": "def2"}
                ]
            }
        """.trimIndent(),
    ) { descriptor ->
        descriptor.variables.hasOrder.shouldBeTrue()
    }

    @Test
    fun `unordered descriptors should remain unordered after serialization`() = doSerializeDeserializeTest(
        """
            {
                "properties": {
                    "a": "def1",
                    "b": "def2"
                }
            }
        """.trimIndent(),
    ) { descriptor ->
        descriptor.variables.hasOrder.shouldBeFalse()
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

    private fun doSerializeDeserializeTest(
        descriptorString: String,
        descriptorChecker: (LibraryDescriptor) -> Unit,
    ) {
        val descriptor1 = parseLibraryDescriptor(descriptorString)
        val text = Json.encodeToString(descriptor1)
        val descriptor = parseLibraryDescriptor(text)
        descriptorChecker(descriptor)
    }
}
