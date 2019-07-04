package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.textResult

enum class ReplCommands(val desc: String) {
    help("display help"),
    classpath("show current classpath")
}

fun isCommand(code: String): Boolean = code.startsWith(":")

fun runCommand(code: String, repl: ReplForJupyter?): ResponseWithMessage {
    val args = code.trim().substring(1).split(" ")
    val cmd =
            try {
                ReplCommands.valueOf(args[0])
            }
            catch (e: IllegalArgumentException) {
                return ResponseWithMessage(ResponseState.Error, textResult("Failed!"), null, "unknown command: $code\nto see available commands, enter :help")
            }
    return when (cmd) {
        ReplCommands.classpath -> ResponseWithMessage(ResponseState.Ok, textResult("current classpath:\n${repl?.classpath}"), null, null)
        ReplCommands.help -> ResponseWithMessage(ResponseState.Ok, textResult("Available commands:\n${ReplCommands.values().joinToString("\n    ", prefix = "    ") { ":${it.name} - ${it.desc}" }}"), null, null)
    }
}
