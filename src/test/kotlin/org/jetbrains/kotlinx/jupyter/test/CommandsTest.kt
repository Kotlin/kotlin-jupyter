package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.common.replCommandOrNull
import org.junit.jupiter.api.Test

class CommandsTest {
    private fun shouldLookLikeReplCommand(
        expectedValid: Boolean,
        code: String,
    ) {
        val actualValid = looksLikeReplCommand(code)
        val expectedValidString = if (expectedValid) "valid" else "invalid"
        actualValid shouldBe expectedValid
    }

    private fun shouldBeCommand(
        code: String,
        expectedCommandValue: String,
    ) {
        shouldLookLikeReplCommand(true, code)
        val actualCommandValue = replCommandOrNull(code).second
        actualCommandValue shouldBe expectedCommandValue
    }

    private fun shouldNotBeCommand(code: String) = shouldLookLikeReplCommand(false, code)

    @Test
    fun `one-line command`() = shouldBeCommand(":help", "help")

    @Test
    fun `command on the third line`() = shouldBeCommand("\n:vars", "vars")

    @Test
    fun `command with the spaces on the first lines`() = shouldBeCommand("\t\n  \n:myCommand", "myCommand")

    @Test
    fun `command with the spaces and newlines after it`() = shouldBeCommand(":command \n   \t\n", "command")

    @Test
    fun `command with digits`() = shouldBeCommand(":usefulCommand42", "usefulCommand42")

    @Test
    fun `command is tolerant to Windows newlines`() = shouldBeCommand("\r  \r\n:windows ", "windows")

    @Test
    fun `command should not be split from the colon`() = shouldNotBeCommand(": help")

    @Test
    fun `no valuable characters should appear in the command code`() = shouldNotBeCommand(":my command")

    @Test
    fun `colon should be the first character in line`() = shouldNotBeCommand(" :notHelp")
}
