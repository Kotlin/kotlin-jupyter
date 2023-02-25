package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.api.RendererPriority
import org.jetbrains.kotlinx.jupyter.util.PriorityList
import org.junit.jupiter.api.Test

class AuxTests {
    @Test
    fun `test priority list`() {
        val list = PriorityList<String>()
        with(list) {
            add("highest", RendererPriority.HIGHEST)
            add("lower", RendererPriority.LOWER)
            add("highest 2", RendererPriority.HIGHEST)
            add("default 1", RendererPriority.DEFAULT)
            add("default 2", RendererPriority.DEFAULT)
        }
        list.toList() shouldBe listOf(
            "highest 2",
            "highest",
            "default 2",
            "default 1",
            "lower",
        )

        list.remove("default 1")
        list.elements().toSet() shouldBe setOf(
            "highest 2",
            "highest",
            "default 2",
            "lower",
        )

        list.add("default 3", RendererPriority.DEFAULT)
        list.elementsWithPriority() shouldBe listOf(
            "highest" to RendererPriority.HIGHEST,
            "lower" to RendererPriority.LOWER,
            "highest 2" to RendererPriority.HIGHEST,
            "default 2" to RendererPriority.DEFAULT,
            "default 3" to RendererPriority.DEFAULT,
        )
    }
}
