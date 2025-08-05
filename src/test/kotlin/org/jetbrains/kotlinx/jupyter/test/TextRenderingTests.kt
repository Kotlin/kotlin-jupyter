package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.api.registerDefaultRenderers
import org.jetbrains.kotlinx.jupyter.codegen.TextRenderersProcessorImpl
import org.junit.jupiter.api.Test

class TextRenderingTests {
    private val processor =
        TextRenderersProcessorImpl().apply {
            registerDefaultRenderers()
        }

    private fun render(obj: Any?): String = processor.renderPreventingRecursion(obj)

    private fun doTest(
        obj: Any?,
        expected: String,
    ) {
        render(obj) shouldBe expected
    }

    private class MyX(
        val v: Int,
    )

    private class MyY(
        val ss: String,
        val x: MyX,
    ) {
        fun f(): Int = 42
    }

    private data class MyD(
        val a: Int,
        val b: Int,
    )

    private class Node(
        val d: Int,
        var parent: Node? = null,
    )

    @Test
    fun `class object rendering`() = doTest(Any::class.java, "class java.lang.Object")

    @Test
    fun `simple list`() = doTest(listOf(1, 2, 3), "ArrayList[1, 2, 3]")

    @Test
    fun `object list`() = doTest(listOf(1, MyX(3), "bar"), "ArrayList[1, MyX(v=3), bar]")

    @Test
    fun `data object`() {
        val d = MyD(1, 2)
        doTest(d, d.toString())
    }

    @Test
    fun `complex object`() = doTest(MyY("ss", MyX(42)), "MyY(ss=ss, x=MyX(v=42))")

    @Test
    fun `simple map`() = doTest(mapOf("a" to 12, "b" to 13), "LinkedHashMap{a => 12, b => 13}")

    @Test
    fun `recursive structure`() {
        val a = Node(1)
        val b = Node(2, a)
        val c = Node(3, b)
        a.parent = c
        doTest(b, "Node(parent=Node(parent=Node(parent=<recursion prevented>, d=3), d=1), d=2)")
    }

    @Test
    fun `boolean rendering`() = doTest(true, "true")
}
