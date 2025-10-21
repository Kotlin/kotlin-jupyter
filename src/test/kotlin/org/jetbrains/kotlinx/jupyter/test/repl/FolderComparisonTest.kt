package org.jetbrains.kotlinx.jupyter.test.repl

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import kotlin.test.assertEquals

class FolderComparisonTest {

    companion object {
        private const val FOLDER_A = "/Users/Ilya.Muradyan/deps/amper"
        private const val FOLDER_B = "/Users/Ilya.Muradyan/deps/old"

        @JvmStatic
        fun filesProvider(): List<Pair<File, File>> {
            val folderA = File(FOLDER_A)
            val folderB = File(FOLDER_B)

            require(folderA.exists() && folderA.isDirectory) { "Folder A doesn't exist or isn't a directory" }
            require(folderB.exists() && folderB.isDirectory) { "Folder B doesn't exist or isn't a directory" }

            return folderA.walkTopDown()
                .filter { it.isFile }
                .mapNotNull { fileA ->
                    val relativePath = fileA.relativeTo(folderA).path
                    val fileB = File(folderB, relativePath)
                    if (fileB.exists()) fileA to fileB else null
                }
                .toList()
        }
    }

    @ParameterizedTest(name = "Compare {0} to {1}")
    @MethodSource("filesProvider")
    fun compareFiles(pair: Pair<File, File>) {
        val (fileA, fileB) = pair
        val contentA = fileA.readText().trim()
        val contentB = fileB.readText().trim()
        assertEquals(
            contentA,
            contentB,
            "Files differ: ${fileA.relativeTo(File(FOLDER_A))} vs ${fileB.relativeTo(File(FOLDER_B))}"
        )
    }
}
