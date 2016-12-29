package org.jetbrains.kotlin.jupyter

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
        ReplCommands.classpath -> ResponseWithMessage(ResponseState.OK, textResult("Ok!"), "current classpath:\n${repl?.classpath?.joinToString("\n  ", prefix = "  ")}", null)
        ReplCommands.help -> ResponseWithMessage(ResponseState.OK, textResult("Ok!"), "Available commands:\n${ReplCommands.values().joinToString("\n    ", prefix = "    ") { ":${it.name} - ${it.desc}" }}", null)
    }
}
