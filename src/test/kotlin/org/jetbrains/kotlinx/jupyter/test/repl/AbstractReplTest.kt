package org.jetbrains.kotlinx.jupyter.test.repl

import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.test.classpath
import java.io.File

abstract class AbstractReplTest {
    val classpathWithTestLib = classpath + File(AbstractReplTest::class.java.protectionDomain.codeSource.location.toURI().path)

    companion object {
        @JvmStatic
        val resolutionInfoProvider = EmptyResolutionInfoProvider

        @JvmStatic
        protected val homeDir = File("")
    }
}
