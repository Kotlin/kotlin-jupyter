package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainValue
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.maps.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.api.VariableStateImpl
import org.jetbrains.kotlinx.jupyter.test.getStringValue
import org.jetbrains.kotlinx.jupyter.test.getValue
import org.jetbrains.kotlinx.jupyter.test.mapToStringValues
import org.junit.jupiter.api.Test

class ReplVarsTest : AbstractSingleReplTest() {
    override val repl = makeSimpleRepl()

    private val varState get() = repl.notebook.variablesState
    private val cellVars get() = repl.notebook.cellVariables

    private fun cellVarsAt(i: Int) = cellVars[i]!!

    private val firstCellVars get() = cellVarsAt(0)
    private val secondCellVars get() = cellVarsAt(1)

    @Test
    fun testVarsStateConsistency() {
        varState.shouldBeEmpty()
        eval(
            """
            val x = 1 
            val y = 0
            val z = 47
            """.trimIndent(),
        )

        varState.mapToStringValues() shouldBe
            mutableMapOf(
                "x" to "1",
                "y" to "0",
                "z" to "47",
            )

        varState.getStringValue("x") shouldBe "1"
        varState.getStringValue("y") shouldBe "0"
        varState.getValue("z") shouldBe 47

        (varState["z"] as VariableStateImpl).update()
        varState.getValue("z") shouldBe 47
    }

    @Test
    fun testVarsEmptyState() {
        val res = eval("3+2")
        val strState = varState.mapToStringValues()
        varState.shouldBeEmpty()
        res.metadata.evaluatedVariablesState shouldBe strState
    }

    @Test
    fun testVarsCapture() {
        eval(
            """
            val x = 1 
            val y = "abc"
            val z = x
            """.trimIndent(),
        )
        varState.mapToStringValues() shouldBe mapOf("x" to "1", "y" to "abc", "z" to "1")
        varState.getValue("x") shouldBe 1
        varState.getStringValue("y") shouldBe "abc"
        varState.getStringValue("z") shouldBe "1"
    }

    @Test
    fun testVarsCaptureSeparateCells() {
        eval(
            """
            val x = 1 
            val y = "abc"
            val z = x
            """.trimIndent(),
        )
        varState.shouldNotBeEmpty()

        eval(
            """
            val x = "abc" 
            var y = 123
            val z = x
            """.trimIndent(),
            1,
        )
        varState shouldHaveSize 3
        varState.getStringValue("x") shouldBe "abc"
        varState.getValue("y") shouldBe 123
        varState.getStringValue("z") shouldBe "abc"

        eval(
            """
            val x = 1024 
            y += 123
            """.trimIndent(),
            2,
        )
        varState shouldHaveSize 3
        varState.getStringValue("x") shouldBe "1024"
        varState.getStringValue("y") shouldBe "${123 * 2}"
        varState.getValue("z") shouldBe "abc"
    }

    @Test
    fun testPrivateVarsCapture() {
        eval(
            """
            private val x = 1 
            private val y = "abc"
            val z = x
            """.trimIndent(),
        )
        varState.mapToStringValues() shouldBe mapOf("x" to "1", "y" to "abc", "z" to "1")
        varState.getValue("x") shouldBe 1
    }

    @Test
    fun testPrivateVarsCaptureSeparateCells() {
        eval(
            """
            private val x = 1 
            private val y = "abc"
            private val z = x
            """.trimIndent(),
        )
        varState.shouldNotBeEmpty()

        eval(
            """
            private val x = "abc" 
            var y = 123
            private val z = x
            """.trimIndent(),
            1,
        )
        varState shouldHaveSize 3
        varState.getStringValue("x") shouldBe "abc"
        varState.getValue("y") shouldBe 123
        varState.getStringValue("z") shouldBe "abc"

        eval(
            """
            private val x = 1024 
            y += x
            """.trimIndent(),
            2,
        )
        varState shouldHaveSize 3
        varState.getStringValue("x") shouldBe "1024"
        varState.getValue("y") shouldBe 123 + 1024
        varState.getStringValue("z") shouldBe "abc"
    }

    @Test
    fun testVarsUsageConsistency() {
        eval("3+2")
        cellVars shouldHaveSize 1
        cellVars.values.first().shouldBeEmpty()
    }

    @Test
    fun testVarsDefsUsage() {
        eval(
            """
            val x = 1
            val z = "abcd"
            var f = 47
            """.trimIndent(),
        )
        cellVars shouldContainValue setOf("z", "f", "x")
    }

    @Test
    fun testVarsDefNRefUsage() {
        eval(
            """
            val x = "abcd"
            var f = 47
            """.trimIndent(),
        )
        cellVars.shouldNotBeEmpty()

        eval(
            """
            val z = 1
            f += f
            """.trimIndent(),
        )
        cellVars shouldContainValue setOf("z", "f", "x")
    }

    @Test
    fun testPrivateVarsDefNRefUsage() {
        eval(
            """
            val x = 124
            private var f = "abcd"
            """.trimIndent(),
        )
        cellVars.shouldNotBeEmpty()

        eval(
            """
            private var z = 1
            z += x
            """.trimIndent(),
        )
        cellVars shouldContainValue setOf("z", "f", "x")
    }

    @Test
    fun testSeparateDefsUsage() {
        eval(
            """
            val x = "abcd"
            var f = 47
            """.trimIndent(),
            1,
        )
        firstCellVars shouldContain "x"

        eval(
            """
            val x = 341
            var f = "abcd"
            """.trimIndent(),
            2,
        )
        cellVars.shouldNotBeEmpty()

        firstCellVars.shouldBeEmpty()
        secondCellVars shouldBe setOf("x", "f")
    }

    @Test
    fun testSeparatePrivateDefsUsage() {
        eval(
            """
            private val x = "abcd"
            private var f = 47
            """.trimIndent(),
            1,
        )
        firstCellVars shouldContain "x"

        eval(
            """
            val x = 341
            private var f = "abcd"
            """.trimIndent(),
            2,
        )
        cellVars.shouldNotBeEmpty()

        firstCellVars.shouldBeEmpty()
        secondCellVars shouldBe setOf("x", "f")
    }

    @Test
    fun testRecursiveVarsState() {
        eval(
            """
            val l = mutableListOf<Any>()
            l.add(listOf(l))
            
            val m = mapOf(1 to l)
            
            val z = setOf(1, 2, 4)
            """.trimIndent(),
            1,
        )
        varState.getStringValue("l") shouldBe "ArrayList: [exception thrown: java.lang.StackOverflowError]"
        varState.getStringValue("m") shouldBe "SingletonMap: [exception thrown: java.lang.StackOverflowError]"
        varState.getStringValue("z") shouldBe "[1, 2, 4]"
    }

    @Test
    fun testSeparatePrivateCellsUsage() {
        eval(
            """
            private val x = "abcd"
            var f = 47
            internal val z = 47
            """.trimIndent(),
            1,
        )
        firstCellVars shouldContain "x"
        firstCellVars shouldContain "z"

        eval(
            """
            private val x = 341
            f += x
            protected val z = "abcd"
            """.trimIndent(),
            2,
        )
        cellVars.shouldNotBeEmpty()

        firstCellVars shouldBe setOf("f")
        secondCellVars shouldBe setOf("x", "f", "z")
    }

    @Test
    fun testVariableModification() {
        eval("var x = sqrt(25.0)", 1)
        varState.getStringValue("x") shouldBe "5.0"
        varState.getValue("x") shouldBe 5.0

        eval("x = x * x", 2)
        varState.getStringValue("x") shouldBe "25.0"
        varState.getValue("x") shouldBe 25.0
    }
}
