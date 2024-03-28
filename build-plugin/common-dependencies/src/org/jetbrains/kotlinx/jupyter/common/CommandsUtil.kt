package org.jetbrains.kotlinx.jupyter.common

private val replCommandRegex = Regex("""^([\r\n\t ]*\n)?:([A-Za-z0-9]*)[\n\t\r ]*$""")

/**
 * If this function returns true for the [code], it will be interpreted as Jupyter REPL command
 */
fun looksLikeReplCommand(code: String): Boolean = replCommandRegex.matches(code)

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
    val match = replCommandRegex.matchEntire(code)!!
    val commandString = match.groupValues[2]
    return ReplCommand.valueOfOrNull(commandString)?.value to commandString
}
