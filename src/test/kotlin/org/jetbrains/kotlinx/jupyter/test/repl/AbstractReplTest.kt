package org.jetbrains.kotlinx.jupyter.test.repl

import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import java.io.File

abstract class AbstractReplTest {
    protected fun String.convertCRLFtoLF(): String {
        return replace("\r\n", "\n")
    }

    companion object {
        @JvmStatic
        val resolutionInfoProvider = EmptyResolutionInfoProvider

        @JvmStatic
        protected val homeDir = File("")
    }
}
