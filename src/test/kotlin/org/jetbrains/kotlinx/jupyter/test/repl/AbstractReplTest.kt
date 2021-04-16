package org.jetbrains.kotlinx.jupyter.test.repl

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.ReplForJupyter
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.repl.CompletionResult
import org.jetbrains.kotlinx.jupyter.repl.ListErrorsResult
import org.jetbrains.kotlinx.jupyter.test.classpath
import java.io.File

abstract class AbstractReplTest {
    val classpathWithTestLib = classpath + File(AbstractReplTest::class.java.protectionDomain.codeSource.location.toURI().path)

    fun ReplForJupyter.listErrorsBlocking(code: String): ListErrorsResult {
        return runBlocking {
            var res: ListErrorsResult? = null
            listErrors(code) {
                res = it
            }
            res!!
        }
    }

    fun ReplForJupyter.completeBlocking(code: String, cursor: Int): CompletionResult {
        return runBlocking {
            var res: CompletionResult? = null
            complete(code, cursor) {
                res = it
            }
            res!!
        }
    }

    companion object {
        @JvmStatic
        val resolutionInfoProvider = EmptyResolutionInfoProvider

        @JvmStatic
        protected val homeDir = File("")
    }
}
