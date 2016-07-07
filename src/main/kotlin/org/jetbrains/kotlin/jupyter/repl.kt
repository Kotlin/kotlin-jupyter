package org.jetbrains.kotlin.jupyter

import com.intellij.openapi.Disposable
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.cli.jvm.repl.*
import org.jetbrains.kotlin.cli.jvm.repl.messages.*
import org.jetbrains.kotlin.cli.jvm.repl.reader.IdeReplCommandReader
import org.jetbrains.kotlin.cli.jvm.repl.reader.ReplCommandReader
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.diagnostics.Diagnostic


class JupyterReplWriter(val conn: JupyterConnection) : ReplWriter {

    override fun printlnWelcomeMessage(x: String) = conn.iopubOut.println(x)
    override fun printlnHelpMessage(x: String) = conn.iopubOut.println(x)
    override fun outputCompileError(x: String) = conn.iopubOut.println(x)
    override fun outputCommandResult(x: String) = conn.iopubOut.println(x)
    override fun outputRuntimeError(x: String) = conn.iopubOut.println(x)

    override fun notifyReadLineStart() {}
    override fun notifyReadLineEnd() {}
    override fun notifyIncomplete() {}
    override fun notifyCommandSuccess() {}
    override fun sendInternalErrorReport(x: String) {}
}

class JupyterReplErrorLogger(val conn: JupyterConnection) : ReplErrorLogger {
    override fun logException(e: Throwable): Nothing {
        throw e
    }
}

class ReplForJupyterConfiguration(val conn: JupyterConnection) : ReplConfiguration {
    override val allowIncompleteLines: Boolean
        get() = false

    override fun onUserCodeExecuting(isExecuting: Boolean) {
//        sinWrapper.isReplScriptExecuting = isExecuting
    }

    override fun createDiagnosticHolder() = ReplTerminalDiagnosticMessageHolder()

    override val writer: ReplWriter = JupyterReplWriter(conn)
    override val errorLogger: ReplErrorLogger = JupyterReplErrorLogger(conn)
    override val commandReader: ReplCommandReader = object : ReplCommandReader {
        override fun flushHistory() {}
        override fun readLine(next: ReplFromTerminal.WhatNextAfterOneLine): String { throw UnsupportedOperationException("not implemented") }
    }
}

class ReplForJupyter(disposable: Disposable, compilerConfiguration: CompilerConfiguration, val conn: JupyterConnection) {

    private val interpreter = ReplInterpreter(disposable, compilerConfiguration, ReplForJupyterConfiguration(conn))

}

fun<T> JupyterConnection.evalWithIO(body: () -> T): T {
    val out = System.out
    System.setOut(iopubOut)
    val err = System.err
    System.setErr(iopubErr)
    val res = body()
    System.setErr(err)
    System.setOut(out)
    return res
}
