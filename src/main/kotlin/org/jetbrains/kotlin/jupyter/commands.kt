package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.Result
import jupyter.kotlin.textResult

enum class ReplCommands(val desc: String) {
    help("display help"),
    classpath("show current classpath")
}

fun isCommand(code: String): Boolean = code.startsWith(":")

fun runCommand(code: String, repl: ReplForJupyter?): Result {
    val args = code.trim().substring(1).split(" ")
    val cmd =
            try {
                ReplCommands.valueOf(args[0])
            }
            catch (e: IllegalArgumentException) {
                return textResult("unknown command: $code\nto see available commands, enter :help")
            }
    return when (cmd) {
        ReplCommands.classpath -> textResult("current classpath:\n${repl?.classpath?.joinToString("\n  ", prefix = "  ")}")
        ReplCommands.help -> textResult("Available commands:\n${ReplCommands.values().joinToString("\n    ", prefix = "    ") { ":${it.name} - ${it.desc}" }}")
    }
}
