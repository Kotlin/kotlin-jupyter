package org.jetbrains.kotlinx.jupyter.test.repl

import org.jetbrains.kotlinx.jupyter.libraries.LibraryFactory
import java.io.File

abstract class AbstractReplTest {
    protected fun String.convertCRLFtoLF(): String {
        return replace("\r\n", "\n")
    }

    companion object {
        @JvmStatic
        val libraryFactory = LibraryFactory.EMPTY

        @JvmStatic
        protected val homeDir = File("")
    }
}
