package org.jetbrains.kotlinx.jupyter.test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.kotlinx.jupyter.util.PatternNameAcceptanceRule
import org.junit.jupiter.api.Test

class PatternAcceptanceRulesTests {
    private fun testPattern(
        pattern: String,
        accepted: List<String> = emptyList(),
        declined: List<String> = emptyList(),
    ) {
        val rule = PatternNameAcceptanceRule(true, pattern)
        accepted.shouldForAll { rule.appliesTo(it).shouldBeTrue() }
        declined.shouldForAll { rule.appliesTo(it).shouldBeFalse() }
    }

    private fun testPatternSerialization(
        expectedRuleStr: String,
        expectedFlag: Boolean,
        expectedPattern: String,
    ) {
        val expectedRule = PatternNameAcceptanceRule(expectedFlag, expectedPattern)
        val expectedJson = JsonPrimitive(expectedRuleStr)
        val actualRule = Json.decodeFromJsonElement<PatternNameAcceptanceRule>(expectedJson)
        val actualRuleJson = Json.encodeToJsonElement(expectedRule)
        actualRuleJson.shouldBeInstanceOf<JsonPrimitive>()
        actualRuleJson.isString.shouldBeTrue()
        val actualRuleStr = actualRuleJson.content

        actualRuleStr shouldBe expectedRuleStr
        actualRule.pattern shouldBe expectedRule.pattern
        actualRule.acceptsFlag shouldBe expectedRule.acceptsFlag
    }

    @Test
    fun `simple case`() =
        testPattern(
            "my.Name0",
            listOf("my.Name0"),
            listOf("my.Name", " my.Name0", "myoName0"),
        )

    @Test
    fun `special characters`() =
        testPattern(
            "org.jetbrains.kotlin?.**.jupyter.*",
            listOf("org.jetbrains.kotlin.my.package.jupyter.Integration", "org.jetbrains.kotlinx.some_package.jupyter.SomeClass"),
            listOf("org.jetbrains.kotlin.my.package.jupyter.integration.MyClass"),
        )

    @Test
    fun `negative pattern serialization`() = testPatternSerialization("-:test.Line_*", false, "test.Line_*")

    @Test
    fun `positive pattern serialization`() = testPatternSerialization("+:test.Line0", true, "test.Line0")

    @Test
    fun `error in serialization`() {
        shouldThrow<SerializationException> {
            testPatternSerialization("+:MyClass:1", true, "MyClass:1")
        }
    }
}
