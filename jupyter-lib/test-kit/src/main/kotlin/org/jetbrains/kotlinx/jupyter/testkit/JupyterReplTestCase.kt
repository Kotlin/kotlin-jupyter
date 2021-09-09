package org.jetbrains.kotlinx.jupyter.testkit

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import jupyter.kotlin.DependsOn
import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.libraries.EmptyResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.repl.EvalResultEx
import kotlin.script.experimental.jvm.util.classpathFromClassloader

abstract class JupyterReplTestCase {
    private val repl = ReplForJupyterImpl(EmptyResolutionInfoProvider, scriptClasspath, isEmbedded = true).apply {
        eval { librariesScanner.addLibrariesFromClassLoader(currentClassLoader, this) }
    }

    fun execEx(code: Code): EvalResultEx {
        return repl.evalEx(code, null, -1, storeHistory)
    }

    fun exec(code: Code): Any? {
        return execEx(code).renderedValue
    }

    fun execRaw(code: Code): Any? {
        return execEx(code).rawValue
    }

    @JvmName("execTyped")
    inline fun <reified T : Any> exec(code: Code): T {
        val res = exec(code)
        res.shouldBeInstanceOf<T>()
        return res
    }

    fun execHtml(code: Code): String {
        val res = exec<MimeTypedResult>(code)
        val html = res["text/html"]
        html.shouldNotBeNull()
        return html
    }

    companion object {
        private val currentClassLoader = DependsOn::class.java.classLoader
        private val scriptClasspath = classpathFromClassloader(currentClassLoader).orEmpty()
    }
}
