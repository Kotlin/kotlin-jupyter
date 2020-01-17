package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.textResult

enum class ReplCommands(val desc: String) {
    help("display help"),
    classpath("show current classpath")
}

fun isCommand(code: String): Boolean = code.startsWith(":")

fun <T> Iterable<T>.joinToStringIndented(transform: ((T) -> CharSequence)? = null) = joinToString("\n    ", prefix = "    ", transform = transform)

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
        ReplCommands.classpath -> {
            val cp = repl!!.currentClasspath
            ResponseWithMessage(ResponseState.Ok, textResult("Current classpath (${cp.count()} paths):\n${cp.joinToString("\n")}"))
        }
        ReplCommands.help -> {
            val commands = ReplCommands.values().asIterable().joinToStringIndented { ":${it.name} - ${it.desc}" }
            val magics = ReplLineMagics.values().asIterable().filter { it.visibleInHelp }.joinToStringIndented {
                var s = "%${it.name} - ${it.desc}"
                if (it.argumentsUsage != null) s += "\n        Usage: %${it.name} ${it.argumentsUsage}"
                s
            }
            val libraries = repl?.resolverConfig?.libraries?.awaitBlocking()?.toList()?.joinToStringIndented {
                "${it.first} ${it.second.link ?: ""}"
            }
            ResponseWithMessage(ResponseState.Ok, textResult("Commands:\n$commands\n\nMagics\n$magics\n\nSupported libraries:\n$libraries"))
        }
    }
}
