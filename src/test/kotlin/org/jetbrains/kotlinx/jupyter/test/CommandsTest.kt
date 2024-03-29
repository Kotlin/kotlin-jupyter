package org.jetbrains.kotlinx.jupyter.test

import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.common.replCommandOrNull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CommandsTest {
    private fun assertLooksLikeReplCommand(
        expectedValid: Boolean,
        code: String,
    ) {
        val actualValid = looksLikeReplCommand(code)
        val expectedValidString = if (expectedValid) "valid" else "invalid"
        assertEquals(expectedValid, actualValid, "Validation failed for code \"$code\", expected it to be $expectedValidString command")
    }

    private fun assertIsCommand(
        code: String,
        expectedCommandValue: String,
    ) {
        assertLooksLikeReplCommand(true, code)
        val actualCommandValue = replCommandOrNull(code).second
        assertEquals(expectedCommandValue, actualCommandValue)
    }

    private fun assertNotCommand(code: String) = assertLooksLikeReplCommand(false, code)

    @Test
    fun `one-line command`() = assertIsCommand(":help", "help")

    @Test
    fun `command on the third line`() = assertIsCommand("\n:vars", "vars")

    @Test
    fun `command with the spaces on the first lines`() = assertIsCommand("\t\n  \n:myCommand", "myCommand")

    @Test
    fun `command with the spaces and newlines after it`() = assertIsCommand(":command \n   \t\n", "command")

    @Test
    fun `command with digits`() = assertIsCommand(":usefulCommand42", "usefulCommand42")

    @Test
    fun `command is tolerant to Windows newlines`() = assertIsCommand("\r  \r\n:windows ", "windows")

    @Test
    fun `command should not be split from the colon`() = assertNotCommand(": help")

    @Test
    fun `no valuable characters should appear in the command code`() = assertNotCommand(":my command")

    @Test
    fun `colon should be the first character in line`() = assertNotCommand(" :notHelp")
}
