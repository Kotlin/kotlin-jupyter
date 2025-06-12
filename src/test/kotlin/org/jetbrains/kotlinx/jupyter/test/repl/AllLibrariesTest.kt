package org.jetbrains.kotlinx.jupyter.test.repl

import org.jetbrains.kotlinx.jupyter.test.KERNEL_LIBRARIES
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.ParameterizedTest.ARGUMENTS_PLACEHOLDER
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.util.stream.Stream
import kotlin.io.path.nameWithoutExtension

/**
 * Run this test if you wish to check that all libraries
 * are resolved successfully
 */
@Execution(ExecutionMode.SAME_THREAD)
@Disabled
class AllLibrariesTest : AbstractSingleReplTest() {
    override val repl = makeReplWithStandardResolver()

    @ParameterizedTest(name = ARGUMENTS_PLACEHOLDER)
    @MethodSource("libraryNames")
    fun testLibrary(libraryName: String) {
        if (libraryName in disabled) {
            println("Library '$libraryName' is skipped")
            return
        }
        val args = arguments[libraryName]?.invoke()?.let { "($it)" }.orEmpty()
        eval("%use $libraryName$args")

        additionalTests[libraryName]?.invoke(this)
    }

    companion object {
        // a lot of heavy dependencies
        // "deeplearning4j",
        // a lot of heavy dependencies
        // "deeplearning4j-cuda",
        // we already have a corresponding test
        // "dataframe",
        // may lead to OOM
        // "spark",
        private val disabled: Set<String> = setOf()

        private val arguments: Map<String, () -> String> =
            mapOf(
                "lib-ext" to { getResourceText("PUBLISHED_JUPYTER_API_VERSION") },
            )

        private val additionalTests: Map<String, AllLibrariesTest.() -> Unit> = mapOf()

        @JvmStatic
        fun libraryNames(): Stream<String> =
            Files
                .walk(KERNEL_LIBRARIES.localLibrariesDir.toPath(), 1)
                .filter { KERNEL_LIBRARIES.isLibraryDescriptor(it.toFile()) }
                .map { it.nameWithoutExtension }

        @Suppress("SameParameterValue")
        private fun getResourceText(name: String): String {
            val clazz = AllLibrariesTest::class.java
            val resource = clazz.classLoader.getResource(name) ?: return ""
            return resource.readText()
        }
    }
}
