package org.jetbrains.kotlinx.jupyter.common

/**
 * If this function returns true for the [code], it will be interpreted as Jupyter REPL command
 */
fun looksLikeReplCommand(code: String): Boolean = code.startsWith(":")

/**
 * Throws [IllegalArgumentException] in case [looksLikeReplCommand] returns false for [code]
 */
fun assertLooksLikeReplCommand(code: String) {
    require(looksLikeReplCommand(code)) { "Code snippet is not a REPL command: $code" }
}

/**
 * Preprocesses REPL command and returns its value,
 * or null in case it's invalid, packed with string used for value
 * extraction
 *
 * @param code Code snippet for which [looksLikeReplCommand] should return true
 * @return Command value or null in case it is not valid, packed with value used for value extraction
 */
fun replCommandOrNull(code: String): Pair<ReplCommand?, String> {
    assertLooksLikeReplCommand(code)
    val commandString = code.trim().substring(1)
    return ReplCommand.valueOfOrNull(commandString) to commandString
}
