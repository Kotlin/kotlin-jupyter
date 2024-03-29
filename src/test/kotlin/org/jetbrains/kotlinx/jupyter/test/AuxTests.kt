package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.api.ProcessingPriority
import org.jetbrains.kotlinx.jupyter.util.PriorityList
import org.junit.jupiter.api.Test

class AuxTests {
    @Test
    fun `test priority list`() {
        val list = PriorityList<String>()
        with(list) {
            add("highest", ProcessingPriority.HIGHEST)
            add("lower", ProcessingPriority.LOWER)
            add("highest 2", ProcessingPriority.HIGHEST)
            add("default 1", ProcessingPriority.DEFAULT)
            add("default 2", ProcessingPriority.DEFAULT)
        }
        list.toList() shouldBe
            listOf(
                "highest 2",
                "highest",
                "default 2",
                "default 1",
                "lower",
            )

        list.remove("default 1")
        list.elements().toSet() shouldBe
            setOf(
                "highest 2",
                "highest",
                "default 2",
                "lower",
            )

        list.add("default 3", ProcessingPriority.DEFAULT)
        list.elementsWithPriority() shouldBe
            listOf(
                "highest" to ProcessingPriority.HIGHEST,
                "lower" to ProcessingPriority.LOWER,
                "highest 2" to ProcessingPriority.HIGHEST,
                "default 2" to ProcessingPriority.DEFAULT,
                "default 3" to ProcessingPriority.DEFAULT,
            )
    }
}
