package org.jetbrains.kotlin.jupyter

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.KotlinVersion
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.jvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.repl.ReplConfiguration
import org.jetbrains.kotlin.cli.jvm.repl.ReplErrorLogger
import org.jetbrains.kotlin.cli.jvm.repl.ReplFromTerminal
import org.jetbrains.kotlin.cli.jvm.repl.ReplInterpreter
import org.jetbrains.kotlin.cli.jvm.repl.messages.ReplTerminalDiagnosticMessageHolder
import org.jetbrains.kotlin.cli.jvm.repl.messages.ReplWriter
import org.jetbrains.kotlin.cli.jvm.repl.reader.ReplCommandReader
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File


class ReplForJupyter(conn: JupyterConnection) {

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

    private val messageCollector = PrintingMessageCollector(conn.iopubErr, MessageRenderer.WITHOUT_PATHS, false)
    private val compilerConfiguration = CompilerConfiguration().apply {
        addJvmClasspathRoots(PathUtil.getJdkClassesRoots())
        addJvmClasspathRoots(conn.config.classpath)
        put(CommonConfigurationKeys.MODULE_NAME, "jupyter")
        put<MessageCollector>(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
        put(JVMConfigurationKeys.INCLUDE_RUNTIME, true)
    }

    val interpreter = ReplInterpreter(conn.disposable, compilerConfiguration, ReplForJupyterConfiguration(conn))

    init {
        log.info("Starting kotlin repl ${KotlinVersion.VERSION}")
        log.info("Using classpath:\n${compilerConfiguration.jvmClasspathRoots.joinToString("\n") { it.canonicalPath }}")
    }
}

fun<T> JupyterConnection.evalWithIO(body: () -> T): T {
    val out = System.out
    System.setOut(iopubOut)
    val err = System.err
    System.setErr(iopubErr)
    val `in` = System.`in`
    System.setIn(stdinIn)
    val res = body()
    System.setIn(`in`)
    System.setErr(err)
    System.setOut(out)
    return res
}
