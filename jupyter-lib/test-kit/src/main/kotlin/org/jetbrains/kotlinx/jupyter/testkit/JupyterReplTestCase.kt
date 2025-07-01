package org.jetbrains.kotlinx.jupyter.testkit

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import jupyter.kotlin.DependsOn
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.MimeTypes
import org.jetbrains.kotlinx.jupyter.repl.EvalRequestData
import org.jetbrains.kotlinx.jupyter.repl.result.EvalResultEx
import kotlin.script.experimental.jvm.util.classpathFromClassloader

abstract class JupyterReplTestCase(
    replProvider: ReplProvider = ReplProvider.withoutLibraryResolution,
) {
    private val repl = replProvider(scriptClasspath)
    val compilerMode = repl.compilerMode

    fun execEx(code: Code): EvalResultEx = repl.evalEx(EvalRequestData(code))

    fun execSuccess(code: Code): EvalResultEx.Success {
        val result = execEx(code)
        result.shouldBeTypeOf<EvalResultEx.Success>()
        return result
    }

    fun execRendered(code: Code): Any? = execSuccess(code).renderedValue

    fun execError(code: Code): Throwable {
        val result = execEx(code)
        result.shouldBeTypeOf<EvalResultEx.Error>()
        return result.error
    }

    fun execRaw(code: Code): Any? = execSuccess(code).internalResult.result.value

    @JvmName("execTyped")
    inline fun <reified T : Any> execRendered(code: Code): T {
        val res = execRendered(code)
        res.shouldBeInstanceOf<T>()
        return res
    }

    fun execHtml(code: Code): String {
        val res = execRendered<MimeTypedResult>(code)
        val html = res[MimeTypes.HTML]
        html.shouldNotBeNull()
        return html
    }

    companion object {
        private val currentClassLoader = DependsOn::class.java.classLoader
        private val scriptClasspath = classpathFromClassloader(currentClassLoader).orEmpty()
    }
}
